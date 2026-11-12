package com.shop.supportservice.ticket;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model of a ticket. State changes go through {@link TicketQueue}, which guards them with the claim.
 */
@Entity
@Table(name = "support_tickets")
public class Ticket {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    private String textMasked;

    private String intent;

    private UUID orderId;

    private Boolean needsHuman;

    private String humanReason;

    @JdbcTypeCode(SqlTypes.JSON)
    private String result;

    private String promptVersion;

    private String model;

    private Integer inputTokens;

    private Integer outputTokens;

    private Long latencyMs;

    private int attempts;

    private Instant createdAt;

    private Instant updatedAt;

    protected Ticket() {
    }

    public static Ticket received(String textMasked, Instant now) {
        var ticket = new Ticket();
        ticket.id = UUID.randomUUID();
        ticket.status = TicketStatus.NEW;
        ticket.textMasked = textMasked;
        ticket.createdAt = now;
        ticket.updatedAt = now;
        return ticket;
    }

    public UUID getId() {
        return id;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public String getTextMasked() {
        return textMasked;
    }

    public String getIntent() {
        return intent;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Boolean getNeedsHuman() {
        return needsHuman;
    }

    public String getHumanReason() {
        return humanReason;
    }

    public String getResult() {
        return result;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getModel() {
        return model;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
