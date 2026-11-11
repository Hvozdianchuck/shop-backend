package com.shop.orderservice.domain;

public interface OrderEvents {

    /** Must be called inside the transaction that saves the order; the event leaves only after commit. */
    void orderPlaced(Order order);
}
