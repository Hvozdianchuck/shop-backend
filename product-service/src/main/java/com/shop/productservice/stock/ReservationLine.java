package com.shop.productservice.stock;

import jakarta.persistence.Embeddable;

@Embeddable
public record ReservationLine(String sku, int quantity) {
}
