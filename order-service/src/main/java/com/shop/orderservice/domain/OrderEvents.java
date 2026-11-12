package com.shop.orderservice.domain;

/** Both methods must be called inside the transaction that changes the order; the event leaves only after commit. */
public interface OrderEvents {

    void orderPlaced(Order order);

    void orderCancelled(Order order);
}
