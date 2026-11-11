package com.shop.orderservice.domain;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public record OrderLine(String sku, int quantity, BigDecimal unitPrice) {

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
