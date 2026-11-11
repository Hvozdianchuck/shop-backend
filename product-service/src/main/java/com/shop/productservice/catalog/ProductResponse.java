package com.shop.productservice.catalog;

import java.math.BigDecimal;

public record ProductResponse(String sku, String name, BigDecimal price, boolean active) {

    static ProductResponse from(Product product) {
        return new ProductResponse(product.getSku(), product.getName(), product.getPrice(), product.isActive());
    }
}
