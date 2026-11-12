package com.shop.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionOperations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    // Any constant works: each service has its own database, so the lock only competes with copies of the same service.
    private static final long LOCK_KEY = 7_420_211L;

    private final JdbcClient jdbc;
    private final TransactionOperations transactions;
    private final KafkaTemplate<String, Object> kafka;
    private final OutboxProperties properties;
    private final Counter failures;

    private Duration backoff = Duration.ZERO;
    private Instant nextAttempt = Instant.MIN;

    public OutboxRelay(JdbcClient jdbc, TransactionOperations transactions, KafkaTemplate<String, Object> kafka,
                       OutboxProperties properties, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.kafka = kafka;
        this.properties = properties;
        this.failures = Counter.builder("outbox.publish.failures").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${shop.outbox.poll-interval:200ms}")
    void poll() {
        if (!properties.relayEnabled() || Instant.now().isBefore(nextAttempt)) {
            return;
        }
        try {
            publishPending();
            backoff = Duration.ZERO;
        } catch (RuntimeException e) {
            failures.increment();
            backoff = backoff.isZero() ? properties.pollInterval() : min(backoff.multipliedBy(2), properties.maxBackoff());
            nextAttempt = Instant.now().plus(backoff);
            log.warn("Outbox publish failed, next attempt in {} ms: {}", backoff.toMillis(), e.getMessage());
        }
    }

    /**
     * Sends one batch and marks it published, all in one transaction.
     * Returns 0 when another copy holds the lock.
     */
    public int publishPending() {
        Integer published = transactions.execute(status -> {
            boolean locked = jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)")
                    .param("key", LOCK_KEY)
                    .query(Boolean.class)
                    .single();
            if (!locked) {
                return 0;
            }
            List<OutboxRecord> batch = jdbc.sql("""
                            SELECT id, topic, msg_key, event_type, payload
                            FROM outbox
                            WHERE published_at IS NULL
                            ORDER BY seq
                            LIMIT :limit
                            """)
                    .param("limit", properties.batchSize())
                    .query(OutboxRecord.class)
                    .list();
            if (batch.isEmpty()) {
                return 0;
            }
            send(batch);
            jdbc.sql("UPDATE outbox SET published_at = now() WHERE id IN (:ids)")
                    .param("ids", batch.stream().map(OutboxRecord::id).toList())
                    .update();
            return batch.size();
        });
        return published == null ? 0 : published;
    }

    private void send(List<OutboxRecord> batch) {
        var futures = new ArrayList<CompletableFuture<SendResult<String, Object>>>();
        for (var record : batch) {
            var message = new ProducerRecord<String, Object>(record.topic(), record.msgKey(), decode(record));
            message.headers()
                    .add(EventHeaders.EVENT_ID, record.id().toString().getBytes(StandardCharsets.UTF_8))
                    .add(EventHeaders.EVENT_TYPE, simpleName(record.eventType()).getBytes(StandardCharsets.UTF_8));
            futures.add(kafka.send(message));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .orTimeout(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .join();
    }

    private static SpecificRecord decode(OutboxRecord record) {
        try {
            var type = Class.forName(record.eventType());
            var data = SpecificData.getForClass(type);
            var schema = data.getSchema(type);
            var reader = new SpecificDatumReader<SpecificRecord>(schema, schema, data);
            return reader.read(null, DecoderFactory.get().jsonDecoder(schema, record.payload()));
        } catch (ClassNotFoundException | IOException e) {
            throw new IllegalStateException("Cannot decode outbox record " + record.id(), e);
        }
    }

    private static String simpleName(String fullName) {
        return fullName.substring(fullName.lastIndexOf('.') + 1);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    record OutboxRecord(UUID id, String topic, String msgKey, String eventType, String payload) {
    }
}
