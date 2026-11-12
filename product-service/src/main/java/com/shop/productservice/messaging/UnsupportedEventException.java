package com.shop.productservice.messaging;

public class UnsupportedEventException extends RuntimeException {

    public UnsupportedEventException(String eventType) {
        super("Unsupported event type: " + eventType);
    }
}
