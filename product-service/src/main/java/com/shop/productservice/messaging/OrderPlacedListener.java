package com.shop.productservice.messaging;

import com.shop.productservice.stock.ReservationLine;
import com.shop.productservice.stock.ReservationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedListener {

    private final ReservationService reservations;
    private final StockEventPublisher stockEvents;

    public OrderPlacedListener(ReservationService reservations, StockEventPublisher stockEvents) {
        this.reservations = reservations;
        this.stockEvents = stockEvents;
    }

    @KafkaListener(topics = KafkaConfig.ORDERS_TOPIC)
    void onOrderPlaced(OrderPlaced event) {
        var lines = event.lines().stream()
                .map(l -> new ReservationLine(l.sku(), l.quantity()))
                .toList();
        var result = reservations.reserve(event.orderId(), lines);
        stockEvents.publish(result);
    }
}
