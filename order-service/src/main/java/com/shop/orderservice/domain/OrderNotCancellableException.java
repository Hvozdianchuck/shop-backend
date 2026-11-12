package com.shop.orderservice.domain;

import java.util.UUID;

public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException(UUID orderId, OrderStatus status) {
        super("Order %s is %s and cannot be cancelled".formatted(orderId, status));
    }
}
