package com.shop.supportservice.ticket;

import com.shop.supportservice.FakeAiConfiguration;
import com.shop.supportservice.TestcontainersConfiguration;
import com.shop.supportservice.tools.ToolJournal;
import com.shop.supportservice.triage.Handoff;
import com.shop.supportservice.triage.TriageDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static com.shop.supportservice.triage.TriageResults.policyAnswer;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeAiConfiguration.class})
class TicketQueueTest {

    @Autowired
    TicketQueue queue;

    @Autowired
    TicketIntake intake;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void emptyQueue() {
        jdbc.sql("DELETE FROM support_tickets").update();
    }

    @Test
    void twoWorkersNeverClaimTheSameTicket() throws Exception {
        for (int i = 0; i < 20; i++) {
            intake.submit("Ticket " + i);
        }
        Callable<List<UUID>> worker = () -> {
            var claimed = new ArrayList<UUID>();
            for (var t = queue.claim(); t.isPresent(); t = queue.claim()) {
                claimed.add(t.get().id());
            }
            return claimed;
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(worker);
            var second = executor.submit(worker);
            var all = new ArrayList<>(first.get());
            all.addAll(second.get());

            assertThat(all).hasSize(20).doesNotHaveDuplicates();
        }
    }

    @Test
    void takesTicketAgainWhenLeaseExpired() {
        var id = intake.submit("Where is my order?").getId();
        var first = queue.claim().orElseThrow();
        assertThat(queue.claim()).isEmpty();

        expireLease(id);

        var second = queue.claim().orElseThrow();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.attempts()).isEqualTo(2);
    }

    @Test
    void ignoresResultOfWorkerThatLostTheLease() {
        var id = intake.submit("Where is my order?").getId();
        var stale = queue.claim().orElseThrow();
        expireLease(id);
        var current = queue.claim().orElseThrow();

        assertThat(queue.complete(stale, decision())).isFalse();
        assertThat(queue.complete(current, decision())).isTrue();
    }

    private void expireLease(UUID id) {
        jdbc.sql("UPDATE support_tickets SET locked_until = now() - interval '1 second' WHERE id = ?").param(id).update();
    }

    private static TriageDecision decision() {
        return new TriageDecision(policyAnswer(), new ToolJournal(), new Handoff(false, null), "v1", "scripted",
                100, 20, 1500, false);
    }
}
