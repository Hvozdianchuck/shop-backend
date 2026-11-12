package com.shop.orderservice.domain;

import com.shop.orderservice.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
class OrderPageQueryTest {

    @Autowired
    OrderRepository orders;

    @Autowired
    EntityManager em;

    Statistics statistics;

    @BeforeEach
    void storeOrders() {
        for (int i = 0; i < 25; i++) {
            orders.save(Order.place(List.of(
                    new OrderLine("MOUSE-1", 1, new BigDecimal("24.90")),
                    new OrderLine("KBD-1", 1, new BigDecimal("89.00")))));
        }
        em.flush();
        em.clear();
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void loadsPageWithLinesInTwoQueriesWhateverThePageSize() {
        assertThat(queriesToLoadPage(5)).isEqualTo(2);
        assertThat(queriesToLoadPage(20)).isEqualTo(2);
    }

    @Test
    void keepsNewestFirstOrder() {
        var page = orders.findPageWithLines(newestFirst(20));

        assertThat(page.getContent()).hasSize(20)
                .isSortedAccordingTo(Comparator.comparing(Order::getCreatedAt).reversed());
        assertThat(page.hasNext()).isTrue();
    }

    private long queriesToLoadPage(int size) {
        em.clear();
        statistics.clear();

        var page = orders.findPageWithLines(newestFirst(size));
        page.forEach(order -> assertThat(order.getLines()).hasSize(2));

        return statistics.getPrepareStatementCount();
    }

    private static PageRequest newestFirst(int size) {
        return PageRequest.of(0, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }
}
