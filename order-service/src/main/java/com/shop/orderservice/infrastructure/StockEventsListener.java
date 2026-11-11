package com.shop.orderservice.infrastructure;

import com.shop.orderservice.application.StockResultHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StockEventsListener {

    private final StockResultHandler handler;

    public StockEventsListener(StockResultHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = KafkaConfig.STOCK_EVENTS_TOPIC)
    void onStockEvent(StockEvent event) {
        switch (event.eventType()) {
            case "StockReserved" -> handler.stockReserved(event.orderId());
            case "StockRejected" -> handler.stockRejected(event.orderId(), event.missingSkus());
            case null, default -> throw new UnsupportedEventException(event.eventType());
        }
    }
}
