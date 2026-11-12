package com.shop.productservice.messaging;

import com.shop.contracts.OrderCancelled;
import com.shop.contracts.OrderPlaced;
import com.shop.messaging.EventHeaders;
import com.shop.messaging.ProcessedEvents;
import com.shop.productservice.stock.InsufficientStockException;
import com.shop.productservice.stock.ReservationLine;
import com.shop.productservice.stock.ReservationService;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ReservationService reservations;
    private final StockEventPublisher stockEvents;
    private final ProcessedEvents processedEvents;
    private final TransactionOperations transaction;

    public OrderEventsListener(ReservationService reservations, StockEventPublisher stockEvents,
                               ProcessedEvents processedEvents, TransactionOperations transaction) {
        this.reservations = reservations;
        this.stockEvents = stockEvents;
        this.processedEvents = processedEvents;
        this.transaction = transaction;
    }

    @KafkaListener(topics = KafkaConfig.ORDERS_TOPIC)
    void onOrderEvent(ConsumerRecord<String, SpecificRecord> record) {
        var eventId = EventHeaders.eventId(record.headers());
        switch (record.value()) {
            case OrderPlaced event -> orderPlaced(eventId, event);
            case OrderCancelled event -> handleOnce(eventId, () -> reservations.release(event.getOrderId()));
            default -> throw new UnsupportedEventException(record.value().getSchema().getName());
        }
    }

    private void orderPlaced(UUID eventId, OrderPlaced event) {
        var lines = event.getLines().stream()
                .map(l -> new ReservationLine(l.getSku(), l.getQuantity()))
                .toList();
        try {
            handleOnce(eventId, () -> stockEvents.publish(reservations.reserve(event.getOrderId(), lines)));
        } catch (InsufficientStockException e) {
            // The first attempt is rolled back as a whole, so the rejection is recorded in a transaction of its own.
            handleOnce(eventId, () -> stockEvents.publish(
                    reservations.reject(event.getOrderId(), lines, e.getMissingSkus())));
        }
    }

    // The event id, the stock change and the outgoing event are committed together, or not at all.
    private void handleOnce(UUID eventId, Runnable handling) {
        transaction.executeWithoutResult(status -> {
            if (processedEvents.markProcessed(eventId)) {
                handling.run();
            } else {
                log.info("Event {} already processed, skipping", eventId);
            }
        });
    }
}
