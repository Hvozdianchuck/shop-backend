package com.shop.supportservice.triage;

import com.shop.supportservice.tools.ToolJournal;

import java.util.UUID;

/**
 * @param result null when the model's reply could not be parsed even after a retry
 */
public record TriageDecision(TriageResult result, ToolJournal journal, Handoff handoff, String promptVersion,
                             String model, int inputTokens, int outputTokens, long latencyMs, boolean retried) {

    public UUID verifiedOrderId() {
        if (result == null || result.orderId() == null) {
            return null;
        }
        return journal.orders().keySet().stream()
                .filter(id -> id.toString().equals(result.orderId()))
                .findFirst()
                .orElse(null);
    }
}
