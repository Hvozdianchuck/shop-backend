package com.shop.orderservice.domain;

public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(Throwable cause) {
        super("Product catalog is unavailable", cause);
    }
}
