package com.shop.orderservice.infrastructure;

import com.shop.orderservice.domain.Order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPlaced(UUID eventId, String eventType, Instant occurredAt, UUID orderId, List<Line> lines) {

    public record Line(String sku, int quantity) {
    }

    static OrderPlaced from(Order order) {
        var lines = order.getLines().stream()
                .map(l -> new Line(l.sku(), l.quantity()))
                .toList();
        return new OrderPlaced(UUID.randomUUID(), "OrderPlaced", Instant.now(), order.getId(), lines);
    }
}
