package com.shop.orderservice.application;

import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StockResultHandler {

    private static final Logger log = LoggerFactory.getLogger(StockResultHandler.class);

    private final OrderRepository orders;

    public StockResultHandler(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional
    public void stockReserved(UUID orderId) {
        var order = pendingOrder(orderId);
        if (order != null) {
            order.confirm();
            log.info("Order {} confirmed", orderId);
        }
    }

    @Transactional
    public void stockRejected(UUID orderId, List<String> missingSkus) {
        var order = pendingOrder(orderId);
        if (order != null) {
            order.reject();
            log.info("Order {} rejected, not enough {}", orderId, missingSkus);
        }
    }

    private Order pendingOrder(UUID orderId) {
        var order = orders.findById(orderId).orElseThrow(() -> new UnknownOrderException(orderId));
        if (!order.isPending()) {
            log.info("Order {} is already {}, ignoring repeated stock result", orderId, order.getStatus());
            return null;
        }
        return order;
    }
}
