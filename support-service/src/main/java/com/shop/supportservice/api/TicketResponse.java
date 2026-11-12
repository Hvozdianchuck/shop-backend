package com.shop.supportservice.api;

import com.shop.supportservice.ticket.Ticket;
import com.shop.supportservice.ticket.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(UUID id, TicketStatus status, String text, Instant createdAt, Instant updatedAt) {

    static TicketResponse from(Ticket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getStatus(), ticket.getTextMasked(),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }
}
