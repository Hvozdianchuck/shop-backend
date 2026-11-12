package com.shop.supportservice.ticket;

import com.shop.supportservice.FakeAiConfiguration;
import com.shop.supportservice.ScriptedChatModel;
import com.shop.supportservice.TestcontainersConfiguration;
import com.shop.supportservice.triage.TriageService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;

import static com.shop.supportservice.triage.TriageResults.json;
import static com.shop.supportservice.triage.TriageResults.policyAnswer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeAiConfiguration.class})
class TicketWorkerTest {

    @Autowired
    TicketQueue queue;

    @Autowired
    TicketIntake intake;

    @Autowired
    TicketRepository tickets;

    @Autowired
    TriageService triage;

    @Autowired
    TicketProperties properties;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @Autowired
    ScriptedChatModel model;

    @Autowired
    JdbcClient jdbc;

    TicketWorker worker;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM support_tickets").update();
        model.reset();
        circuitBreakers.circuitBreaker("model").reset();
        worker = new TicketWorker(queue, triage, properties, circuitBreakers);
    }

    @Test
    void storesTriageResultWithPromptVersionAndTokens() {
        var id = intake.submit("Can I return a mouse I bought last week?").getId();
        model.reply("You can return it within 14 days.").reply(json(policyAnswer()));

        worker.processWaiting();

        var ticket = tickets.findById(id).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.TRIAGED);
        assertThat(ticket.getIntent()).isEqualTo("POLICY_QUESTION");
        assertThat(ticket.getResult()).contains("\"replyDraft\": \"You can return it within 14 days.\"");
        assertThat(ticket.getPromptVersion()).isEqualTo("v1");
        assertThat(ticket.getModel()).isEqualTo("scripted");
        assertThat(ticket.getInputTokens()).isEqualTo(200);
    }

    @Test
    void handsTicketToPersonAfterThreeFailedAttempts() {
        var id = intake.submit("Hello?").getId();
        for (int i = 0; i < 3; i++) {
            model.fail(new IllegalStateException("Ollama is down"));
        }

        for (int i = 0; i < 3; i++) {
            worker.processWaiting();
        }

        var ticket = tickets.findById(id).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEEDS_HUMAN);
        assertThat(ticket.getHumanReason()).isEqualTo("assistant unavailable");
        assertThat(ticket.getAttempts()).isEqualTo(3);
    }

    @Test
    void leavesTicketsWaitingWhileModelCircuitIsOpen() {
        var id = intake.submit("Hello?").getId();
        circuitBreakers.circuitBreaker("model").transitionToOpenState();

        worker.processWaiting();

        var ticket = tickets.findById(id).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(ticket.getAttempts()).isZero();
    }

    @Test
    void picksTicketsUpAgainOnceCircuitHalfOpens() {
        var id = intake.submit("Can I return a mouse?").getId();
        circuitBreakers.circuitBreaker("model").transitionToOpenState();
        model.reply("You can return it within 14 days.").reply(json(policyAnswer()));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            worker.processWaiting();
            assertThat(tickets.findById(id).orElseThrow().getStatus()).isEqualTo(TicketStatus.TRIAGED);
        });
    }
}
