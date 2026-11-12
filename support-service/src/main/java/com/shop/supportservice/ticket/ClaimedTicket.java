package com.shop.supportservice.ticket;

import java.util.UUID;

/**
 * @param attempts also works as a fencing token: only the worker holding the latest attempt may finish the ticket
 */
public record ClaimedTicket(UUID id, String text, int attempts) {
}
