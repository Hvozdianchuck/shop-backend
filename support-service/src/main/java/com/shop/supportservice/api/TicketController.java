package com.shop.supportservice.api;

import com.shop.supportservice.ticket.TicketIntake;
import com.shop.supportservice.ticket.TicketRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/support/tickets")
public class TicketController {

    private final TicketIntake intake;
    private final TicketRepository tickets;

    public TicketController(TicketIntake intake, TicketRepository tickets) {
        this.intake = intake;
        this.tickets = tickets;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> submit(@Valid @RequestBody SubmitTicketRequest request) {
        var ticket = intake.submit(request.text());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(ticket.getId())
                .toUri();
        return ResponseEntity.accepted().location(location).body(TicketResponse.from(ticket));
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable UUID id) {
        return tickets.findById(id)
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket " + id + " not found"));
    }
}
