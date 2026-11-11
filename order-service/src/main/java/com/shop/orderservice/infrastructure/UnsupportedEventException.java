package com.shop.orderservice.infrastructure;

public class UnsupportedEventException extends RuntimeException {

    public UnsupportedEventException(String eventType) {
        super("Unsupported event type: " + eventType);
    }
}
