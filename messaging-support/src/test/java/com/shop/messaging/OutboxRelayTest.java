package com.shop.messaging;

import com.shop.contracts.OrderCancelled;
import com.shop.contracts.OrderLine;
import com.shop.contracts.OrderPlaced;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OutboxRelayTest {

    @Autowired
    OutboxWriter writer;

    @Autowired
    OutboxRelay relay;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    KafkaConnectionDetails kafka;

    @Autowired
    Tracer tracer;

    @BeforeEach
    void emptyOutbox() {
        jdbc.sql("DELETE FROM outbox").update();
    }

    @Test
    void refusesToWriteWithoutTransaction() {
        assertThatThrownBy(() -> writer.append("orders", "o-1", cancelled(UUID.randomUUID())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void sendsEventsInOrderWithIdAndTypeHeaders() {
        var orderId = UUID.randomUUID();
        var topic = "relay-" + orderId;
        var placedId = transactions.execute(s -> writer.append(topic, orderId.toString(), placed(orderId)));
        var cancelledId = transactions.execute(s -> writer.append(topic, orderId.toString(), cancelled(orderId)));

        assertThat(relay.publishPending()).isEqualTo(2);

        var records = consume(topic, 2);
        assertThat(records).extracting(ConsumerRecord::key).containsOnly(orderId.toString());
        assertThat(records).extracting(r -> header(r, "eventId"))
                .containsExactly(placedId.toString(), cancelledId.toString());
        assertThat(records).extracting(r -> header(r, "eventType"))
                .containsExactly("OrderPlaced", "OrderCancelled");
        var placed = (OrderPlaced) records.getFirst().value();
        assertThat(placed.getLines().getFirst().getUnitPrice()).isEqualByComparingTo("19.99");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE published_at IS NULL").query(Long.class).single())
                .isZero();
    }

    @Test
    void continuesTraceOfTheRequestThatWroteTheEvent() {
        var orderId = UUID.randomUUID();
        var topic = "relay-" + orderId;
        var span = tracer.nextSpan().name("place order").start();
        try (var ignored = tracer.withSpan(span)) {
            transactions.executeWithoutResult(s -> writer.append(topic, orderId.toString(), cancelled(orderId)));
        } finally {
            span.end();
        }

        relay.publishPending();

        var traceId = span.context().traceId();
        assertThat(jdbc.sql("SELECT headers ->> 'traceparent' FROM outbox WHERE aggregate_id = ?")
                .param(orderId.toString()).query(String.class).single()).contains(traceId);
        assertThat(header(consume(topic, 1).getFirst(), "traceparent")).contains(traceId);
    }

    @Test
    void doesNothingWhileAnotherCopyHoldsTheLock() throws Exception {
        var orderId = UUID.randomUUID();
        transactions.executeWithoutResult(s -> writer.append("relay-locked", orderId.toString(), cancelled(orderId)));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var otherCopy = Thread.ofVirtual().start(() -> transactions.executeWithoutResult(s -> {
            jdbc.sql("SELECT pg_advisory_xact_lock(7420211)").query().singleValue();
            locked.countDown();
            await(release);
        }));
        locked.await(5, TimeUnit.SECONDS);

        int published = relay.publishPending();
        release.countDown();
        otherCopy.join();

        assertThat(published).isZero();
        assertThat(relay.publishPending()).isEqualTo(1);
    }

    private List<ConsumerRecord<String, Object>> consume(String topic, int expected) {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                "schema.registry.url", "mock://messaging-test",
                "specific.avro.reader", true);
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), avroDeserializer(config))) {
            consumer.subscribe(List.of(topic));
            var records = new ArrayList<ConsumerRecord<String, Object>>();
            var deadline = Instant.now().plusSeconds(15);
            while (records.size() < expected && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
            return records;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static org.apache.kafka.common.serialization.Deserializer<Object> avroDeserializer(Map<String, Object> config) {
        var deserializer = new KafkaAvroDeserializer();
        deserializer.configure((Map) config, false);
        return (org.apache.kafka.common.serialization.Deserializer) deserializer;
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static OrderPlaced placed(UUID orderId) {
        return new OrderPlaced(orderId, Instant.now().truncatedTo(ChronoUnit.MILLIS),
                List.of(new OrderLine("KBD-1", 2, new BigDecimal("19.99"))), "EUR");
    }

    private static OrderCancelled cancelled(UUID orderId) {
        return new OrderCancelled(orderId, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
