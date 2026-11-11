package com.shop.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxCleanupTest {

    @Autowired
    OutboxCleanup cleanup;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM processed_events").update();
    }

    @Test
    void deletesOnlyRecordsPastRetention() {
        var oldPublished = outboxRecord("now() - interval '4 days'", "now() - interval '4 days'");
        var oldUnpublished = outboxRecord("now() - interval '4 days'", "NULL");
        var recentPublished = outboxRecord("now() - interval '1 day'", "now() - interval '1 day'");
        var oldEvent = processedEvent("now() - interval '15 days'");
        var recentEvent = processedEvent("now() - interval '10 days'");

        cleanup.deleteOldRecords();

        assertThat(jdbc.sql("SELECT id FROM outbox").query(UUID.class).list())
                .containsExactlyInAnyOrder(oldUnpublished, recentPublished)
                .doesNotContain(oldPublished);
        assertThat(jdbc.sql("SELECT event_id FROM processed_events").query(UUID.class).list())
                .containsExactly(recentEvent)
                .doesNotContain(oldEvent);
    }

    private UUID outboxRecord(String createdAt, String publishedAt) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO outbox (id, aggregate_id, event_type, topic, msg_key, payload, created_at, published_at)
                        VALUES (:id, 'o-1', 'Test', 'orders', 'o-1', '{}', %s, %s)
                        """.formatted(createdAt, publishedAt))
                .param("id", id)
                .update();
        return id;
    }

    private UUID processedEvent(String processedAt) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO processed_events (event_id, processed_at) VALUES (:id, %s)".formatted(processedAt))
                .param("id", id)
                .update();
        return id;
    }
}
