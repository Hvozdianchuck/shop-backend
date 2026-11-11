package com.shop.productservice.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPlaced(UUID eventId, String eventType, Instant occurredAt, UUID orderId, List<Line> lines) {

    public record Line(String sku, int quantity) {
    }
}
