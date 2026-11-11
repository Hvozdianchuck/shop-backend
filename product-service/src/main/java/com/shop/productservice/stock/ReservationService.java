package com.shop.productservice.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final StockRepository stock;
    private final ReservationRepository reservations;
    private final TransactionOperations transaction;

    public ReservationService(StockRepository stock, ReservationRepository reservations,
                              TransactionOperations transaction) {
        this.stock = stock;
        this.reservations = reservations;
        this.transaction = transaction;
    }

    public ReservationResult reserve(UUID orderId, List<ReservationLine> requested) {
        var lines = mergeBySku(requested);
        try {
            return transaction.execute(status -> takeAll(orderId, lines));
        } catch (InsufficientStockException e) {
            log.info("Order {} rejected, not enough {}", orderId, e.missingSkus);
            return transaction.execute(status -> reservations.save(Reservation.rejected(orderId, lines, e.missingSkus)).result());
        }
    }

    private ReservationResult takeAll(UUID orderId, List<ReservationLine> lines) {
        var missing = new ArrayList<String>();
        // Lines are sorted by SKU, so two orders always lock stock rows in the same order and cannot deadlock.
        for (var line : lines) {
            if (stock.take(line.sku(), line.quantity()) == 0) {
                missing.add(line.sku());
            }
        }
        if (!missing.isEmpty()) {
            var unknown = missing.stream().filter(sku -> !stock.existsById(sku)).toList();
            if (!unknown.isEmpty()) {
                throw new UnknownSkuException(unknown);
            }
            throw new InsufficientStockException(missing);
        }
        log.info("Stock reserved for order {}", orderId);
        return reservations.save(Reservation.reserved(orderId, lines)).result();
    }

    private static List<ReservationLine> mergeBySku(List<ReservationLine> lines) {
        Map<String, Integer> quantities = lines.stream()
                .collect(Collectors.toMap(ReservationLine::sku, ReservationLine::quantity, Integer::sum, TreeMap::new));
        return quantities.entrySet().stream()
                .map(e -> new ReservationLine(e.getKey(), e.getValue()))
                .toList();
    }

    private static class InsufficientStockException extends RuntimeException {

        private final List<String> missingSkus;

        InsufficientStockException(List<String> missingSkus) {
            super(null, null, false, false);
            this.missingSkus = List.copyOf(missingSkus);
        }
    }
}
