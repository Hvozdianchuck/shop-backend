package com.shop.productservice.messaging;

import com.shop.productservice.stock.ReservationResult;
import com.shop.productservice.stock.ReservationStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class StockEventPublisher {

    private final KafkaTemplate<String, Object> kafka;

    public StockEventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    // Waits for the broker: if the send fails, the listener fails too and the OrderPlaced record is read again.
    public void publish(ReservationResult result) {
        Object event = result.status() == ReservationStatus.RESERVED
                ? StockReserved.of(result.orderId())
                : StockRejected.of(result.orderId(), result.missingSkus());
        kafka.send(KafkaConfig.STOCK_EVENTS_TOPIC, result.orderId().toString(), event).join();
    }
}
