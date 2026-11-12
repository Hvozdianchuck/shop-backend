package com.shop.orderservice.application;

import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderEvents;
import com.shop.orderservice.domain.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CancelOrderService {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderService.class);

    private final OrderRepository orders;
    private final OrderEvents events;

    public CancelOrderService(OrderRepository orders, OrderEvents events) {
        this.orders = orders;
        this.events = events;
    }

    @Transactional
    public Order cancel(UUID orderId) {
        var order = orders.findWithLinesById(orderId).orElseThrow(() -> new UnknownOrderException(orderId));
        if (order.cancel()) {
            events.orderCancelled(order);
            log.info("Order {} cancelled", orderId);
        }
        return order;
    }
}
