package com.shop.supportservice.ticket;

import com.shop.supportservice.privacy.PiiMasker;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TicketIntake {

    static final int MAX_LENGTH = 2000;
    static final String TRUNCATED = "\n[truncated]";

    private final PiiMasker masker;
    private final TicketRepository tickets;

    public TicketIntake(PiiMasker masker, TicketRepository tickets) {
        this.masker = masker;
        this.tickets = tickets;
    }

    public Ticket submit(String text) {
        var masked = masker.mask(text.strip());
        if (masked.length() > MAX_LENGTH) {
            masked = masked.substring(0, MAX_LENGTH) + TRUNCATED;
        }
        return tickets.save(Ticket.received(masked, Instant.now()));
    }
}
