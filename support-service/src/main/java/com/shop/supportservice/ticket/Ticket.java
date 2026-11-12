package com.shop.supportservice.ticket;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
public class Ticket {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    private String textMasked;

    private int attempts;

    private Instant lockedUntil;

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
