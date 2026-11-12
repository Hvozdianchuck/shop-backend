package com.shop.supportservice.tools;

public class OrderLookupException extends RuntimeException {

    public OrderLookupException(Throwable cause) {
        super("Order service is unavailable", cause);
    }
}
