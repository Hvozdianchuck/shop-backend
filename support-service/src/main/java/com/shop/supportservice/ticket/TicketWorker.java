package com.shop.supportservice.ticket;

import com.shop.supportservice.triage.TriageService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(name = "shop.tickets.worker-enabled", matchIfMissing = true)
public class TicketWorker {

    static final String ASSISTANT_UNAVAILABLE = "assistant unavailable";

    private static final Logger log = LoggerFactory.getLogger(TicketWorker.class);

    private final TicketQueue queue;
    private final TriageService triage;
    private final TicketProperties properties;
    private final CircuitBreaker modelCircuit;

    public TicketWorker(TicketQueue queue, TriageService triage, TicketProperties properties,
                        CircuitBreakerRegistry circuitBreakers) {
        this.queue = queue;
        this.triage = triage;
        this.properties = properties;
        this.modelCircuit = circuitBreakers.circuitBreaker("model");
    }

    // While the model is down, tickets stay in the queue instead of burning their attempts.
    @Scheduled(fixedDelayString = "${shop.tickets.poll-interval:2s}")
    public void processWaiting() {
        while (modelCircuit.getState() != CircuitBreaker.State.OPEN) {
            var ticket = queue.claim();
            if (ticket.isEmpty()) {
                return;
            }
            process(ticket.get());
        }
    }

    void process(ClaimedTicket ticket) {
        if (ticket.attempts() > properties.maxAttempts()) {
            queue.handToHuman(ticket, ASSISTANT_UNAVAILABLE);
            return;
        }
        try {
            var decision = triage.triage(ticket.text());
            if (queue.complete(ticket, decision)) {
                log.info("Ticket {} triaged in {} ms, needs human: {}", ticket.id(), decision.latencyMs(),
                        decision.handoff().needsHuman());
            }
        } catch (RuntimeException e) {
            if (ticket.attempts() >= properties.maxAttempts()) {
                log.warn("Ticket {} handed to a person after {} failed attempts: {}", ticket.id(), ticket.attempts(), e.toString());
                queue.handToHuman(ticket, ASSISTANT_UNAVAILABLE);
            } else {
                log.info("Triage of ticket {} failed on attempt {}, will retry: {}", ticket.id(), ticket.attempts(), e.toString());
                queue.retryLater(ticket);
            }
        }
    }
}
