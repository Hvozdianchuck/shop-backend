package com.shop.orderservice.application;

import java.util.UUID;

public class UnknownOrderException extends RuntimeException {

    public UnknownOrderException(UUID orderId) {
        super("Order " + orderId + " does not exist");
    }
}
