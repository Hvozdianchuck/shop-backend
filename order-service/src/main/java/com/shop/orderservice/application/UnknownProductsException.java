package com.shop.orderservice.application;

import java.util.List;

public class UnknownProductsException extends RuntimeException {

    private final List<String> skus;

    public UnknownProductsException(List<String> skus) {
        super("Unknown or inactive products: " + skus);
        this.skus = List.copyOf(skus);
    }

    public List<String> getSkus() {
        return skus;
    }
}
