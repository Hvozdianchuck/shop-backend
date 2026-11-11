package com.shop.orderservice.infrastructure;

import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KafkaOrderEvents implements OrderEvents {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEvents.class);

    private final ApplicationEventPublisher publisher;
    private final KafkaTemplate<String, Object> kafka;

    public KafkaOrderEvents(ApplicationEventPublisher publisher, KafkaTemplate<String, Object> kafka) {
        this.publisher = publisher;
        this.kafka = kafka;
    }

    @Override
    public void orderPlaced(Order order) {
        publisher.publishEvent(OrderPlaced.from(order));
    }

    @TransactionalEventListener
    void send(OrderPlaced event) {
        kafka.send(KafkaConfig.ORDERS_TOPIC, event.orderId().toString(), event)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        log.error("OrderPlaced for order {} was not sent, the order stays PENDING", event.orderId(), e);
                    }
                });
    }
}
