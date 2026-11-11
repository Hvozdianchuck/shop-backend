package com.shop.productservice.messaging;

import com.shop.productservice.TestcontainersConfiguration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, OrderPlacedListenerTest.OrdersTopic.class})
class OrderPlacedListenerTest {

    @TestConfiguration
    static class OrdersTopic {

        @Bean
        NewTopic ordersTopic() {
            return TopicBuilder.name(KafkaConfig.ORDERS_TOPIC).partitions(3).replicas(1).build();
        }
    }

    @Autowired
    KafkaConnectionDetails kafka;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    Producer<String, String> producer;
    Consumer<String, String> stockEvents;
    Consumer<String, String> deadLetters;

    @BeforeEach
    void connect() {
        var servers = kafka.getBootstrapServers();
        producer = new KafkaProducer<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers),
                new StringSerializer(), new StringSerializer());
        stockEvents = consumeFromEnd(servers, KafkaConfig.STOCK_EVENTS_TOPIC);
        deadLetters = consumeFromEnd(servers, KafkaConfig.ORDERS_DLT);
    }

    @AfterEach
    void close() {
        producer.close();
        stockEvents.close();
        deadLetters.close();
    }

    @Test
    void reservesStockAndPublishesStockReserved() {
        var sku = product(5);
        var orderId = UUID.randomUUID();

        sendOrderPlaced(orderId, sku, 2);

        var event = stockEventFor(orderId);
        assertThat(event.get("eventType").asString()).isEqualTo("StockReserved");
        assertThat(event.get("eventId").asString()).isNotBlank();
        assertThat(available(sku)).isEqualTo(3);
    }

    @Test
    void publishesStockRejectedAndKeepsStockWhenNotEnough() {
        var sku = product(1);
        var orderId = UUID.randomUUID();

        sendOrderPlaced(orderId, sku, 2);

        var event = stockEventFor(orderId);
        assertThat(event.get("eventType").asString()).isEqualTo("StockRejected");
        assertThat(event.get("missingSkus").values()).extracting(JsonNode::asString).containsExactly(sku);
        assertThat(available(sku)).isEqualTo(1);
    }

    @Test
    void answersDuplicateOrderPlacedAgainWithoutSecondReservation() {
        var sku = product(5);
        var orderId = UUID.randomUUID();

        sendOrderPlaced(orderId, sku, 2);
        sendOrderPlaced(orderId, sku, 2);

        assertThat(stockEventFor(orderId).get("eventType").asString()).isEqualTo("StockReserved");
        assertThat(stockEventFor(orderId).get("eventType").asString()).isEqualTo("StockReserved");
        assertThat(available(sku)).isEqualTo(3);
    }

    @Test
    void sendsUnreadableEventToDeadLetterTopicAsIs() {
        var key = UUID.randomUUID().toString();

        send(key, "{not json");

        var record = recordFor(deadLetters, key);
        assertThat(record.value()).isEqualTo("{not json");
        assertThat(header(record, "kafka_dlt-original-topic")).isEqualTo(KafkaConfig.ORDERS_TOPIC);
        assertThat(header(record, "kafka_dlt-original-offset")).isNotNull();
        assertThat(header(record, "kafka_dlt-exception-fqcn")).endsWith("DeserializationException");
    }

    @Test
    void sendsUnknownSkuToDeadLetterTopicWithoutRetriesAndKeepsGoing() {
        var orderId = UUID.randomUUID();
        var sku = product(5);
        var nextOrderId = UUID.randomUUID();
        var start = Instant.now();

        sendOrderPlaced(orderId, "NO-SUCH-SKU", 1);
        sendOrderPlaced(nextOrderId, sku, 1);

        var record = recordFor(deadLetters, orderId.toString());
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(1));
        assertThat(header(record, "kafka_dlt-exception-cause-fqcn")).endsWith("UnknownSkuException");
        assertThat(json.readTree(record.value()).get("orderId").asString()).isEqualTo(orderId.toString());
        assertThat(stockEventFor(nextOrderId).get("eventType").asString()).isEqualTo("StockReserved");
    }

    private void sendOrderPlaced(UUID orderId, String sku, int quantity) {
        send(orderId.toString(), """
                {"eventId":"%s","eventType":"OrderPlaced","occurredAt":"%s","orderId":"%s",
                 "lines":[{"sku":"%s","quantity":%d}]}""".formatted(UUID.randomUUID(), Instant.now(), orderId, sku, quantity));
    }

    private void send(String key, String value) {
        producer.send(new ProducerRecord<>(KafkaConfig.ORDERS_TOPIC, key, value));
        producer.flush();
    }

    private JsonNode stockEventFor(UUID orderId) {
        var record = recordFor(stockEvents, orderId.toString());
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
        return json.readTree(record.value());
    }

    private static ConsumerRecord<String, String> recordFor(Consumer<String, String> consumer, String key) {
        var deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No record with key " + key);
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static Consumer<String, String> consumeFromEnd(List<String> servers, String topic) {
        var consumer = new KafkaConsumer<>(Map.<String, Object>of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servers),
                new StringDeserializer(), new StringDeserializer());
        var partitions = consumer.partitionsFor(topic).stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
        return consumer;
    }

    private String product(int available) {
        var sku = "T-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.sql("insert into products (sku, name, price) values (?, 'Test product', 10.00)").param(sku).update();
        jdbc.sql("insert into stock (sku, available) values (?, ?)").params(sku, available).update();
        return sku;
    }

    private int available(String sku) {
        return jdbc.sql("select available from stock where sku = ?").param(sku).query(Integer.class).single();
    }
}
