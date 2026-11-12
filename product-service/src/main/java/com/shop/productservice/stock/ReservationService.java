package com.shop.productservice.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    public ReservationService(StockRepository stock, ReservationRepository reservations) {
        this.stock = stock;
        this.reservations = reservations;
    }

    /**
     * Takes stock for every line or throws {@link InsufficientStockException}. The caller's transaction then rolls
     * back, which also returns stock already taken for the other lines.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ReservationResult reserve(UUID orderId, List<ReservationLine> requested) {
        var lines = mergeBySku(requested);
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

    @Transactional(propagation = Propagation.MANDATORY)
    public ReservationResult reject(UUID orderId, List<ReservationLine> requested, List<String> missingSkus) {
        log.info("Order {} rejected, not enough {}", orderId, missingSkus);
        return reservations.save(Reservation.rejected(orderId, mergeBySku(requested), missingSkus)).result();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID orderId) {
        var lines = reservations.findById(orderId).map(Reservation::release).orElse(List.of());
        if (lines.isEmpty()) {
            log.info("Order {} cancelled, no stock to return", orderId);
            return;
        }
        lines.forEach(line -> stock.giveBack(line.sku(), line.quantity()));
        log.info("Stock returned for cancelled order {}", orderId);
    }

    private static List<ReservationLine> mergeBySku(List<ReservationLine> lines) {
        Map<String, Integer> quantities = lines.stream()
                .collect(Collectors.toMap(ReservationLine::sku, ReservationLine::quantity, Integer::sum, TreeMap::new));
        return quantities.entrySet().stream()
                .map(e -> new ReservationLine(e.getKey(), e.getValue()))
                .toList();
    }
}
