package com.shop.orderservice.infrastructure;

import com.shop.contracts.StockRejected;
import com.shop.contracts.StockReserved;
import com.shop.messaging.EventHeaders;
import com.shop.messaging.ProcessedEvents;
import com.shop.orderservice.application.StockResultHandler;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StockEventsListener {

    private static final Logger log = LoggerFactory.getLogger(StockEventsListener.class);

    private final StockResultHandler handler;
    private final ProcessedEvents processedEvents;

    public StockEventsListener(StockResultHandler handler, ProcessedEvents processedEvents) {
        this.handler = handler;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(topics = KafkaConfig.STOCK_EVENTS_TOPIC)
    @Transactional
    void onStockEvent(ConsumerRecord<String, SpecificRecord> record) {
        var eventId = EventHeaders.eventId(record.headers());
        if (!processedEvents.markProcessed(eventId)) {
            log.info("Event {} already processed, skipping", eventId);
            return;
        }
        switch (record.value()) {
            case StockReserved event -> handler.stockReserved(event.getOrderId());
            case StockRejected event -> handler.stockRejected(event.getOrderId(), event.getMissingSkus());
            default -> throw new UnsupportedEventException(record.value().getSchema().getName());
        }
    }
}
