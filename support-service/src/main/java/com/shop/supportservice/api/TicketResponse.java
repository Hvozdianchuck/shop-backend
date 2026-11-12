package com.shop.supportservice.api;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.shop.supportservice.ticket.Ticket;
import com.shop.supportservice.ticket.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * @param result the assistant's answer as stored, including the reply draft for the agent
 */
public record TicketResponse(UUID id, TicketStatus status, String text, String intent, UUID orderId,
                             Boolean needsHuman, String humanReason, @JsonRawValue String result,
                             String promptVersion, String model, Integer inputTokens, Integer outputTokens,
                             Long latencyMs, int attempts, Instant createdAt, Instant updatedAt) {

    static TicketResponse from(Ticket t) {
        return new TicketResponse(t.getId(), t.getStatus(), t.getTextMasked(), t.getIntent(), t.getOrderId(),
                t.getNeedsHuman(), t.getHumanReason(), t.getResult(), t.getPromptVersion(), t.getModel(),
                t.getInputTokens(), t.getOutputTokens(), t.getLatencyMs(), t.getAttempts(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
