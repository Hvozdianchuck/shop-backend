package com.shop.productservice.stock;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private String[] missingSkus;

    @ElementCollection
    @CollectionTable(name = "reservation_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<ReservationLine> lines = new ArrayList<>();

    private Instant createdAt;

    protected Reservation() {
    }

    private Reservation(UUID orderId, ReservationStatus status, List<ReservationLine> lines, List<String> missingSkus) {
        this.orderId = orderId;
        this.status = status;
        this.lines.addAll(lines);
        this.missingSkus = missingSkus.toArray(String[]::new);
        this.createdAt = Instant.now();
    }

    static Reservation reserved(UUID orderId, List<ReservationLine> lines) {
        return new Reservation(orderId, ReservationStatus.RESERVED, lines, List.of());
    }

    static Reservation rejected(UUID orderId, List<ReservationLine> lines, List<String> missingSkus) {
        return new Reservation(orderId, ReservationStatus.REJECTED, lines, missingSkus);
    }

    /** Returns the lines to put back on stock; nothing if stock was never taken or is already back. */
    List<ReservationLine> release() {
        if (status != ReservationStatus.RESERVED) {
            return List.of();
        }
        status = ReservationStatus.RELEASED;
        return List.copyOf(lines);
    }

    public ReservationResult result() {
        return new ReservationResult(orderId, status, List.of(missingSkus));
    }
}
