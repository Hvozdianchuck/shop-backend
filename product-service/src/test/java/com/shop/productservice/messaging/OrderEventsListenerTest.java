package com.shop.productservice.messaging;

import com.shop.contracts.OrderCancelled;
import com.shop.contracts.OrderLine;
import com.shop.contracts.OrderPlaced;
import com.shop.contracts.StockRejected;
import com.shop.contracts.StockReserved;
import com.shop.productservice.KafkaTestClients;
import com.shop.productservice.TestcontainersConfiguration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.shop.productservice.KafkaTestClients.avroConsumerFromEnd;
import static com.shop.productservice.KafkaTestClients.avroProducer;
import static com.shop.productservice.KafkaTestClients.bytesConsumerFromEnd;
import static com.shop.productservice.KafkaTestClients.bytesProducer;
import static com.shop.productservice.KafkaTestClients.header;
import static com.shop.productservice.KafkaTestClients.recordFor;
import static com.shop.productservice.KafkaTestClients.recordsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import({TestcontainersConfiguration.class, OrderEventsListenerTest.OrdersTopic.class})
class OrderEventsListenerTest {

    @TestConfiguration
    static class OrdersTopic {

        @Bean
        NewTopic ordersTopic() {
            return TopicBuilder.name(KafkaConfig.ORDERS_TOPIC).partitions(3).replicas(1).build();
        }
    }

    @Autowired
    JdbcClient jdbc;

    Producer<String, Object> producer;
    Consumer<String, Object> stockEvents;
    Consumer<String, byte[]> deadLetters;

    @BeforeEach
    void connect() {
        producer = avroProducer();
        stockEvents = avroConsumerFromEnd(KafkaConfig.STOCK_EVENTS_TOPIC);
        deadLetters = bytesConsumerFromEnd(KafkaConfig.ORDERS_DLT);
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

        sendOrderPlaced(orderId, sku, 2, UUID.randomUUID());

        var record = recordFor(stockEvents, orderId.toString());
        assertThat(record.value()).isInstanceOf(StockReserved.class);
        assertThat(header(record, "eventType")).isEqualTo("StockReserved");
        assertThat(header(record, "eventId")).isNotBlank();
        assertThat(available(sku)).isEqualTo(3);
    }

    @Test
    void publishesStockRejectedAndKeepsStockWhenNotEnough() {
        var sku = product(1);
        var orderId = UUID.randomUUID();

        sendOrderPlaced(orderId, sku, 2, UUID.randomUUID());

        var event = recordFor(stockEvents, orderId.toString()).value();
        assertThat(event).isInstanceOfSatisfying(StockRejected.class,
                rejected -> assertThat(rejected.getMissingSkus()).containsExactly(sku));
        assertThat(available(sku)).isEqualTo(1);
    }

    @Test
    void handlesRedeliveredEventOnce() {
        var sku = product(5);
        var orderId = UUID.randomUUID();
        var eventId = UUID.randomUUID();

        sendOrderPlaced(orderId, sku, 2, eventId);
        sendOrderPlaced(orderId, sku, 2, eventId);

        assertThat(recordsFor(stockEvents, orderId.toString(), Duration.ofSeconds(5), 2)).hasSize(1);
        assertThat(available(sku)).isEqualTo(3);
    }

    @Test
    void returnsStockWhenReservedOrderIsCancelled() {
        var sku = product(5);
        var orderId = UUID.randomUUID();
        sendOrderPlaced(orderId, sku, 2, UUID.randomUUID());
        recordFor(stockEvents, orderId.toString());

        KafkaTestClients.send(producer, KafkaConfig.ORDERS_TOPIC, orderId.toString(),
                new OrderCancelled(orderId, Instant.now()), UUID.randomUUID());

        await().atMost(Duration.ofSeconds(10)).until(() -> available(sku) == 5);
        assertThat(jdbc.sql("select status from reservations where order_id = ?").param(orderId)
                .query(String.class).single()).isEqualTo("RELEASED");
    }

    @Test
    void sendsUnreadableRecordToDeadLetterTopicAsIs() {
        var key = UUID.randomUUID().toString();
        var garbage = "not avro".getBytes(StandardCharsets.UTF_8);

        try (var raw = bytesProducer()) {
            raw.send(new ProducerRecord<>(KafkaConfig.ORDERS_TOPIC, key, garbage));
        }

        var record = recordFor(deadLetters, key);
        assertThat(record.value()).isEqualTo(garbage);
        assertThat(header(record, "kafka_dlt-original-topic")).isEqualTo(KafkaConfig.ORDERS_TOPIC);
        assertThat(header(record, "kafka_dlt-exception-fqcn")).endsWith("DeserializationException");
    }

    @Test
    void sendsUnknownSkuToDeadLetterTopicWithoutRetriesAndKeepsGoing() {
        var orderId = UUID.randomUUID();
        var sku = product(5);
        var nextOrderId = UUID.randomUUID();
        var start = Instant.now();

        sendOrderPlaced(orderId, "NO-SUCH-SKU", 1, UUID.randomUUID());
        sendOrderPlaced(nextOrderId, sku, 1, UUID.randomUUID());

        var record = recordFor(deadLetters, orderId.toString());
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(1));
        assertThat(header(record, "kafka_dlt-exception-cause-fqcn")).endsWith("UnknownSkuException");
        assertThat(recordFor(stockEvents, nextOrderId.toString()).value()).isInstanceOf(StockReserved.class);
    }

    private void sendOrderPlaced(UUID orderId, String sku, int quantity, UUID eventId) {
        var event = new OrderPlaced(orderId, Instant.now(), List.of(new OrderLine(sku, quantity, new BigDecimal("10.00"))));
        KafkaTestClients.send(producer, KafkaConfig.ORDERS_TOPIC, orderId.toString(), event, eventId);
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
