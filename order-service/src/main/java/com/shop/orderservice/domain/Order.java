package com.shop.orderservice.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal total;

    @ElementCollection
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderLine> lines = new ArrayList<>();

    @Version
    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    protected Order() {
    }

    private Order(List<OrderLine> lines) {
        this.id = UUID.randomUUID();
        this.status = OrderStatus.PENDING;
        this.lines.addAll(lines);
        this.total = lines.stream().map(OrderLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Order place(List<OrderLine> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one line");
        }
        return new Order(lines);
    }

    public void confirm() {
        moveTo(OrderStatus.CONFIRMED);
    }

    public void reject() {
        moveTo(OrderStatus.REJECTED);
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    private void moveTo(OrderStatus target) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order %s is %s, cannot move to %s".formatted(id, status, target));
        }
        status = target;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
