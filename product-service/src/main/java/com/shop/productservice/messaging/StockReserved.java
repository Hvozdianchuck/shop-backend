package com.shop.productservice.messaging;

import java.time.Instant;
import java.util.UUID;

public record StockReserved(UUID eventId, String eventType, Instant occurredAt, UUID orderId) {

    static StockReserved of(UUID orderId) {
        return new StockReserved(UUID.randomUUID(), "StockReserved", Instant.now(), orderId);
    }
}
