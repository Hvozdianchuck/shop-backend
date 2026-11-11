package com.shop.productservice.stock;

import java.util.List;
import java.util.UUID;

public record ReservationResult(UUID orderId, ReservationStatus status, List<String> missingSkus) {
}
