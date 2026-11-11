package com.shop.productservice.stock;

import com.shop.productservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReservationServiceTest {

    @Autowired
    ReservationService service;

    @Autowired
    ReservationRepository reservations;

    @Autowired
    JdbcClient jdbc;

    @Test
    void reservesAllLinesAndTakesStock() {
        var mouse = product(10);
        var cable = product(3);
        var orderId = UUID.randomUUID();

        var result = service.reserve(orderId, List.of(new ReservationLine(mouse, 2), new ReservationLine(cable, 3)));

        assertThat(result).isEqualTo(new ReservationResult(orderId, ReservationStatus.RESERVED, List.of()));
        assertThat(available(mouse)).isEqualTo(8);
        assertThat(available(cable)).isZero();
    }

    @Test
    void rejectsWholeOrderWhenOneLineIsShort() {
        var mouse = product(10);
        var monitor = product(1);
        var orderId = UUID.randomUUID();

        var result = service.reserve(orderId, List.of(new ReservationLine(mouse, 2), new ReservationLine(monitor, 2)));

        assertThat(result).isEqualTo(new ReservationResult(orderId, ReservationStatus.REJECTED, List.of(monitor)));
        assertThat(available(mouse)).isEqualTo(10);
        assertThat(available(monitor)).isEqualTo(1);
        assertThat(reservations.findById(orderId)).get()
                .extracting(Reservation::result)
                .isEqualTo(result);
    }

    @Test
    void failsOnUnknownSkuWithoutTakingStock() {
        var mouse = product(10);
        var orderId = UUID.randomUUID();

        assertThatThrownBy(() -> service.reserve(orderId,
                List.of(new ReservationLine(mouse, 1), new ReservationLine("NO-SUCH-SKU", 1))))
                .isInstanceOf(UnknownSkuException.class);

        assertThat(available(mouse)).isEqualTo(10);
        assertThat(reservations.findById(orderId)).isEmpty();
    }

    @Test
    void lastUnitGoesToExactlyOneOfTwoConcurrentOrders() throws Exception {
        var monitor = product(1);
        var start = new CountDownLatch(1);
        List<CompletableFuture<ReservationResult>> results;

        try (var pool = Executors.newFixedThreadPool(2)) {
            results = List.of(1, 2).stream()
                    .map(i -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return service.reserve(UUID.randomUUID(), List.of(new ReservationLine(monitor, 1)));
                    }, pool))
                    .toList();
            start.countDown();
        }

        assertThat(results).extracting(f -> f.join().status())
                .containsExactlyInAnyOrder(ReservationStatus.RESERVED, ReservationStatus.REJECTED);
        assertThat(available(monitor)).isZero();
    }

    private String product(int available) {
        var sku = "T-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.sql("insert into products (sku, name, price) values (?, 'Test product', 10.00)").param(sku).update();
        jdbc.sql("insert into stock (sku, available) values (?, ?)").params(sku, available).update();
        return sku;
    }

    private int available(String sku) {
        return jdbc.sql("select available from stock where sku = ?").param(sku).query(Integer.class).single();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
