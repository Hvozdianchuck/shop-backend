package com.shop.productservice.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockRejected(UUID eventId, String eventType, Instant occurredAt, UUID orderId, List<String> missingSkus) {

    static StockRejected of(UUID orderId, List<String> missingSkus) {
        return new StockRejected(UUID.randomUUID(), "StockRejected", Instant.now(), orderId, missingSkus);
    }
}
