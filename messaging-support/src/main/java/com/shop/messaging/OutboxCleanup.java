package com.shop.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.time.Instant;

public class OutboxCleanup {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanup.class);

    private final JdbcClient jdbc;
    private final OutboxProperties properties;

    public OutboxCleanup(JdbcClient jdbc, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Scheduled(cron = "${shop.outbox.cleanup-cron:0 30 3 * * *}")
    public void deleteOldRecords() {
        var now = Instant.now();
        int outbox = jdbc.sql("DELETE FROM outbox WHERE published_at < :before")
                .param("before", Timestamp.from(now.minus(properties.publishedRetention())))
                .update();
        int processed = jdbc.sql("DELETE FROM processed_events WHERE processed_at < :before")
                .param("before", Timestamp.from(now.minus(properties.processedEventsRetention())))
                .update();
        log.info("Deleted {} published outbox records and {} processed event ids", outbox, processed);
    }
}
