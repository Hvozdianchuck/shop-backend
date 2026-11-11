package com.shop.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public class ProcessedEvents {

    private final JdbcClient jdbc;
    private final Counter duplicates;

    public ProcessedEvents(JdbcClient jdbc, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.duplicates = Counter.builder("events.duplicates").register(meterRegistry);
    }

    /**
     * Records the event id in the caller's transaction. Returns false if the event was already processed;
     * the caller then skips it. If the caller's transaction rolls back, the id is not recorded either.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(UUID eventId) {
        int inserted = jdbc.sql("INSERT INTO processed_events (event_id) VALUES (:id) ON CONFLICT DO NOTHING")
                .param("id", eventId)
                .update();
        if (inserted == 0) {
            duplicates.increment();
            return false;
        }
        return true;
    }
}
