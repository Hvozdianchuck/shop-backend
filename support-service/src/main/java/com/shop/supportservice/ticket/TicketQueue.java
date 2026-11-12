package com.shop.supportservice.ticket;

import com.shop.supportservice.triage.TriageDecision;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

@Component
public class TicketQueue {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final TicketProperties properties;

    public TicketQueue(JdbcClient jdbc, JsonMapper json, TicketProperties properties) {
        this.jdbc = jdbc;
        this.json = json;
        this.properties = properties;
    }

    /**
     * Takes the oldest waiting ticket, or one whose lease ran out because its worker died.
     * SKIP LOCKED lets several workers claim different tickets at the same time.
     */
    public Optional<ClaimedTicket> claim() {
        return jdbc.sql("""
                        UPDATE support_tickets
                        SET status = 'PROCESSING', attempts = attempts + 1,
                            locked_until = now() + make_interval(secs => :lease), updated_at = now()
                        WHERE id = (SELECT id FROM support_tickets
                                    WHERE status IN ('NEW', 'PROCESSING')
                                      AND (locked_until IS NULL OR locked_until < now())
                                    ORDER BY created_at
                                    LIMIT 1
                                    FOR UPDATE SKIP LOCKED)
                        RETURNING id, text_masked, attempts
                        """)
                .param("lease", properties.lease().toSeconds())
                .query((rs, n) -> new ClaimedTicket(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3)))
                .optional();
    }

    public boolean complete(ClaimedTicket ticket, TriageDecision decision) {
        var result = decision.result();
        var status = decision.handoff().needsHuman() ? TicketStatus.NEEDS_HUMAN : TicketStatus.TRIAGED;
        var intent = result == null || result.intent() == null ? null : result.intent().name();
        return jdbc.sql("""
                        UPDATE support_tickets
                        SET status = :status, intent = :intent, order_id = :orderId, needs_human = :needsHuman,
                            human_reason = :reason, result = CAST(:result AS jsonb), prompt_version = :promptVersion,
                            model = :model, input_tokens = :inputTokens, output_tokens = :outputTokens,
                            latency_ms = :latencyMs, locked_until = NULL, updated_at = now()
                        WHERE id = :id AND attempts = :attempts AND status = 'PROCESSING'
                        """)
                .param("status", status.name())
                .param("intent", intent)
                .param("orderId", decision.verifiedOrderId())
                .param("needsHuman", decision.handoff().needsHuman())
                .param("reason", decision.handoff().reason())
                .param("result", result == null ? null : json.writeValueAsString(result))
                .param("promptVersion", decision.promptVersion())
                .param("model", decision.model())
                .param("inputTokens", decision.inputTokens())
                .param("outputTokens", decision.outputTokens())
                .param("latencyMs", decision.latencyMs())
                .param("id", ticket.id())
                .param("attempts", ticket.attempts())
                .update() == 1;
    }

    public void retryLater(ClaimedTicket ticket) {
        jdbc.sql("""
                        UPDATE support_tickets
                        SET status = 'NEW', locked_until = now() + make_interval(secs => :delay), updated_at = now()
                        WHERE id = :id AND attempts = :attempts AND status = 'PROCESSING'
                        """)
                .param("delay", properties.retryDelay().toSeconds())
                .param("id", ticket.id())
                .param("attempts", ticket.attempts())
                .update();
    }

    public void handToHuman(ClaimedTicket ticket, String reason) {
        jdbc.sql("""
                        UPDATE support_tickets
                        SET status = 'NEEDS_HUMAN', needs_human = TRUE, human_reason = :reason,
                            locked_until = NULL, updated_at = now()
                        WHERE id = :id AND attempts = :attempts AND status = 'PROCESSING'
                        """)
                .param("reason", reason)
                .param("id", ticket.id())
                .param("attempts", ticket.attempts())
                .update();
    }
}
