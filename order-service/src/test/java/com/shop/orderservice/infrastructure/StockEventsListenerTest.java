package com.shop.orderservice.infrastructure;

import com.shop.orderservice.TestcontainersConfiguration;
import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderLine;
import com.shop.orderservice.domain.OrderRepository;
import com.shop.orderservice.domain.OrderStatus;
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
import org.springframework.kafka.config.TopicBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import({TestcontainersConfiguration.class, StockEventsListenerTest.StockEventsTopic.class})
class StockEventsListenerTest {

    @TestConfiguration
    static class StockEventsTopic {

        @Bean
        NewTopic stockEventsTopic() {
            return TopicBuilder.name(KafkaConfig.STOCK_EVENTS_TOPIC).partitions(3).replicas(1).build();
        }
    }

    @Autowired
    OrderRepository orders;

    @Autowired
    KafkaConnectionDetails kafka;

    Producer<String, String> producer;
    Consumer<String, String> deadLetters;

    @BeforeEach
    void connect() {
        producer = new KafkaProducer<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                new StringSerializer(), new StringSerializer());
        deadLetters = new KafkaConsumer<>(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                new StringDeserializer(), new StringDeserializer());
        var partitions = deadLetters.partitionsFor(KafkaConfig.STOCK_EVENTS_DLT).stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .toList();
        deadLetters.assign(partitions);
        deadLetters.seekToEnd(partitions);
        partitions.forEach(deadLetters::position);
    }

    @AfterEach
    void close() {
        producer.close();
        deadLetters.close();
    }

    @Test
    void confirmsOrderWhenStockIsReserved() {
        var orderId = pendingOrder();

        send(orderId, "StockReserved", "");

        await().atMost(Duration.ofSeconds(10)).until(() -> status(orderId) == OrderStatus.CONFIRMED);
    }

    @Test
    void rejectsOrderWhenStockIsShort() {
        var orderId = pendingOrder();

        send(orderId, "StockRejected", ",\"missingSkus\":[\"MON-27\"]");

        await().atMost(Duration.ofSeconds(10)).until(() -> status(orderId) == OrderStatus.REJECTED);
    }

    @Test
    void ignoresLateResultForOrderThatIsAlreadyDecided() {
        var orderId = pendingOrder();
        send(orderId, "StockRejected", ",\"missingSkus\":[\"MON-27\"]");
        await().atMost(Duration.ofSeconds(10)).until(() -> status(orderId) == OrderStatus.REJECTED);

        send(orderId, "StockReserved", "");

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3))
                .until(() -> status(orderId) == OrderStatus.REJECTED);
        assertThat(deadLetters.poll(Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void sendsUnknownEventTypeToDeadLetterTopic() {
        var orderId = pendingOrder();

        send(orderId, "StockTeleported", "");

        var record = deadLetterFor(orderId);
        assertThat(header(record, "kafka_dlt-original-topic")).isEqualTo(KafkaConfig.STOCK_EVENTS_TOPIC);
        assertThat(header(record, "kafka_dlt-exception-cause-fqcn")).endsWith("UnsupportedEventException");
        assertThat(status(orderId)).isEqualTo(OrderStatus.PENDING);
    }

    private UUID pendingOrder() {
        return orders.save(Order.place(List.of(new OrderLine("MOUSE-1", 1, new BigDecimal("24.90"))))).getId();
    }

    private OrderStatus status(UUID orderId) {
        return orders.findById(orderId).orElseThrow().getStatus();
    }

    private void send(UUID orderId, String eventType, String extraFields) {
        var event = """
                {"eventId":"%s","eventType":"%s","occurredAt":"%s","orderId":"%s"%s}"""
                .formatted(UUID.randomUUID(), eventType, Instant.now(), orderId, extraFields);
        producer.send(new ProducerRecord<>(KafkaConfig.STOCK_EVENTS_TOPIC, orderId.toString(), event));
        producer.flush();
    }

    private ConsumerRecord<String, String> deadLetterFor(UUID orderId) {
        var deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline)) {
            for (var record : deadLetters.poll(Duration.ofMillis(200))) {
                if (orderId.toString().equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No dead letter for order " + orderId);
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
