package com.shop.supportservice.tools;

import java.util.Optional;
import java.util.UUID;

public interface OrderLookup {

    /**
     * Empty when the order does not exist; {@link OrderLookupException} when order-service cannot answer.
     */
    Optional<OrderSummary> find(UUID orderId);
}
