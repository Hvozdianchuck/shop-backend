package com.shop.orderservice.api;

import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, OrderStatus status, BigDecimal total, List<Line> lines,
                            Instant createdAt, Instant updatedAt) {

    public record Line(String sku, int quantity, BigDecimal unitPrice) {
    }

    static OrderResponse from(Order order) {
        var lines = order.getLines().stream()
                .map(l -> new Line(l.sku(), l.quantity(), l.unitPrice()))
                .toList();
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotal(), lines,
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
