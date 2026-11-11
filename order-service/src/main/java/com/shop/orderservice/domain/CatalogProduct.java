package com.shop.orderservice.domain;

import java.math.BigDecimal;

public record CatalogProduct(String sku, BigDecimal price, boolean active) {
}
