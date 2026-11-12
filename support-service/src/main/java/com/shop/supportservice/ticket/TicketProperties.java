package com.shop.supportservice.ticket;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param lease      how long a claimed ticket stays hidden from other workers; after that it is taken again
 * @param retryDelay pause before a ticket whose triage failed is tried again
 */
@ConfigurationProperties("shop.tickets")
public record TicketProperties(Duration lease, int maxAttempts, Duration retryDelay) {
}
