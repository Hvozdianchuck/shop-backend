package com.shop.orderservice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class OrderTest {

    private final List<OrderLine> lines = List.of(
            new OrderLine("MOUSE-1", 2, new BigDecimal("24.90")),
            new OrderLine("HDMI-2M", 1, new BigDecimal("9.50")));

    @Test
    void newOrderIsPendingWithTotalFromFixedPrices() {
        var order = Order.place(lines);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotal()).isEqualByComparingTo("59.30");
    }

    @Test
    void pendingOrderCanBeConfirmedOrRejected() {
        var confirmed = Order.place(lines);
        var rejected = Order.place(lines);

        confirmed.confirm();
        rejected.reject();

        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void finalStatusCannotChange() {
        var order = Order.place(lines);
        order.confirm();

        assertThatIllegalStateException().isThrownBy(order::reject);
        assertThatIllegalStateException().isThrownBy(order::confirm);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void orderWithoutLinesIsNotAllowed() {
        assertThatIllegalArgumentException().isThrownBy(() -> Order.place(List.of()));
    }
}
