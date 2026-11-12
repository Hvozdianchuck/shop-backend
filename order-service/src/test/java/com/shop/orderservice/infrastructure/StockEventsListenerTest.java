package com.shop.orderservice.infrastructure;

import com.shop.contracts.OrderCancelled;
import com.shop.contracts.StockRejected;
import com.shop.contracts.StockReserved;
import com.shop.orderservice.KafkaTestClients;
import com.shop.orderservice.TestcontainersConfiguration;
import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderLine;
import com.shop.orderservice.domain.OrderRepository;
import com.shop.orderservice.domain.OrderStatus;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.shop.orderservice.KafkaTestClients.avroProducer;
import static com.shop.orderservice.KafkaTestClients.bytesConsumerFromEnd;
import static com.shop.orderservice.KafkaTestClients.header;
import static com.shop.orderservice.KafkaTestClients.recordFor;
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

    Producer<String, Object> producer;
    Consumer<String, byte[]> deadLetters;

    @BeforeEach
    void connect() {
        producer = avroProducer();
        deadLetters = bytesConsumerFromEnd(KafkaConfig.STOCK_EVENTS_DLT);
    }

    @AfterEach
    void close() {
        producer.close();
        deadLetters.close();
    }

    @Test
    void confirmsOrderWhenStockIsReserved() {
        var orderId = pendingOrder();

        send(orderId, new StockReserved(orderId, Instant.now()));

        await().atMost(Duration.ofSeconds(10)).until(() -> status(orderId) == OrderStatus.CONFIRMED);
    }

    @Test
    void rejectsOrderWhenStockIsShort() {
        var orderId = pendingOrder();

        send(orderId, new StockRejected(orderId, Instant.now(), List.of("MON-27")));

        await().atMost(Duration.ofSeconds(10)).until(() -> status(orderId) == OrderStatus.REJECTED);
    }

    @Test
    void ignoresLateResultForOrderThatIsAlreadyDecided() {
        var orderId = pendingOrder();
        send(orderId, new StockRejected(orderId, Instant.now(), List.of("MON-27")));
        await().atMost(Duration.ofSeconds(10)).until(() -> status(orderId) == OrderStatus.REJECTED);

        send(orderId, new StockReserved(orderId, Instant.now()));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3))
                .until(() -> status(orderId) == OrderStatus.REJECTED);
        assertThat(deadLetters.poll(Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void sendsUnexpectedEventTypeToDeadLetterTopic() {
        var orderId = pendingOrder();

        send(orderId, new OrderCancelled(orderId, Instant.now()));

        var record = recordFor(deadLetters, orderId.toString());
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

    private void send(UUID orderId, SpecificRecord event) {
        KafkaTestClients.send(producer, KafkaConfig.STOCK_EVENTS_TOPIC, orderId.toString(), event, UUID.randomUUID());
    }
}
