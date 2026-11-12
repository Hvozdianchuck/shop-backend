package com.shop.productservice.messaging;

import com.shop.contracts.StockRejected;
import com.shop.contracts.StockReserved;
import com.shop.messaging.OutboxWriter;
import com.shop.productservice.stock.ReservationResult;
import com.shop.productservice.stock.ReservationStatus;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class StockEventPublisher {

    private final OutboxWriter outbox;

    public StockEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void publish(ReservationResult result) {
        SpecificRecord event = result.status() == ReservationStatus.RESERVED
                ? new StockReserved(result.orderId(), Instant.now())
                : new StockRejected(result.orderId(), Instant.now(), result.missingSkus());
        outbox.append(KafkaConfig.STOCK_EVENTS_TOPIC, result.orderId().toString(), event);
    }
}
