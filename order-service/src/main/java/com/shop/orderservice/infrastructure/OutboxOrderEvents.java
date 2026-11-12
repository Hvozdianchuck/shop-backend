package com.shop.orderservice.infrastructure;

import com.shop.contracts.OrderCancelled;
import com.shop.contracts.OrderPlaced;
import com.shop.messaging.OutboxWriter;
import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderEvents;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OutboxOrderEvents implements OrderEvents {

    private final OutboxWriter outbox;

    public OutboxOrderEvents(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void orderPlaced(Order order) {
        var lines = order.getLines().stream()
                .map(l -> new com.shop.contracts.OrderLine(l.sku(), l.quantity(), l.unitPrice()))
                .toList();
        var event = new OrderPlaced(order.getId(), Instant.now(), lines);
        outbox.append(KafkaConfig.ORDERS_TOPIC, order.getId().toString(), event);
    }

    @Override
    public void orderCancelled(Order order) {
        var event = new OrderCancelled(order.getId(), Instant.now());
        outbox.append(KafkaConfig.ORDERS_TOPIC, order.getId().toString(), event);
    }
}
