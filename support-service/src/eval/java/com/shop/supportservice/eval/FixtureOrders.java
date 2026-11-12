package com.shop.supportservice.eval;

import com.shop.supportservice.tools.OrderLookup;
import com.shop.supportservice.tools.OrderSummary;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orders from the dataset instead of order-service, so every run sees the same data.
 */
class FixtureOrders implements OrderLookup {

    private final Map<UUID, OrderSummary> orders = new ConcurrentHashMap<>();

    void add(EvalCase.Order order) {
        orders.put(order.id(), new OrderSummary(order.id(), order.status(), order.createdAt(), order.itemCount()));
    }

    @Override
    public Optional<OrderSummary> find(UUID orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }
}
