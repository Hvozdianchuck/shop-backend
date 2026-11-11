package com.shop.orderservice.domain;

import com.shop.orderservice.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class OrderRepositoryTest {

    @Autowired
    OrderRepository orders;

    @Autowired
    EntityManager em;

    @Test
    void storesOrderWithLinesAndFixedPrices() {
        var order = Order.place(List.of(
                new OrderLine("MOUSE-1", 2, new BigDecimal("24.90")),
                new OrderLine("KBD-1", 1, new BigDecimal("89.00"))));
        orders.saveAndFlush(order);
        em.clear();

        var loaded = orders.findWithLinesById(order.getId()).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(loaded.getTotal()).isEqualByComparingTo("138.80");
        assertThat(loaded.getLines()).containsExactlyInAnyOrder(
                new OrderLine("MOUSE-1", 2, new BigDecimal("24.90")),
                new OrderLine("KBD-1", 1, new BigDecimal("89.00")));
    }

    @Test
    void persistsStatusChange() {
        var order = orders.saveAndFlush(Order.place(List.of(new OrderLine("MON-27", 1, new BigDecimal("279.00")))));

        order.confirm();
        orders.saveAndFlush(order);
        em.clear();

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
