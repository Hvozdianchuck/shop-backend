package com.shop.supportservice.tools;

import java.time.Instant;
import java.util.UUID;

public record OrderSummary(UUID id, String status, Instant createdAt, int itemCount) {
}
