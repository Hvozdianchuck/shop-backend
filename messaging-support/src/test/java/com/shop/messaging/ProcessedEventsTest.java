package com.shop.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProcessedEventsTest {

    @Autowired
    ProcessedEvents processedEvents;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void reportsSecondDeliveryAsDuplicate() {
        var eventId = UUID.randomUUID();
        double before = meterRegistry.counter("events.duplicates").count();

        boolean first = transactions.execute(s -> processedEvents.markProcessed(eventId));
        boolean second = transactions.execute(s -> processedEvents.markProcessed(eventId));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(meterRegistry.counter("events.duplicates").count()).isEqualTo(before + 1);
    }

    @Test
    void forgetsEventWhenProcessingRollsBack() {
        var eventId = UUID.randomUUID();

        transactions.executeWithoutResult(s -> {
            processedEvents.markProcessed(eventId);
            s.setRollbackOnly();
        });
        boolean retried = transactions.execute(s -> processedEvents.markProcessed(eventId));

        assertThat(retried).isTrue();
    }

    @Test
    void refusesToRunWithoutTransaction() {
        assertThatThrownBy(() -> processedEvents.markProcessed(UUID.randomUUID()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
