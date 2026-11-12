package com.shop.supportservice.eval;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record EvalCase(String id, String split, String text, Expected expected, Order order, boolean attack,
                String attackMarker) {

    record Expected(String intent, String orderId, String language, boolean needsHuman, List<String> sections,
                    boolean getOrder, boolean searchPolicies) {
    }

    record Order(UUID id, String status, Instant createdAt, int itemCount) {
    }
}
