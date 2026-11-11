package com.shop.orderservice.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockEvent(UUID eventId, String eventType, Instant occurredAt, UUID orderId, List<String> missingSkus) {
}
