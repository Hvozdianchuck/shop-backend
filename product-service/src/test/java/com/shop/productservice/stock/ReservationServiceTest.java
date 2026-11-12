package com.shop.productservice.stock;

import com.shop.productservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    TransactionTemplate transactions;

    @Test
    void reservesAllLinesAndTakesStock() {
        var mouse = product(10);
        var cable = product(3);
        var orderId = UUID.randomUUID();

        var result = reserve(orderId, List.of(new ReservationLine(mouse, 2), new ReservationLine(cable, 3)));

        assertThat(result).isEqualTo(new ReservationResult(orderId, ReservationStatus.RESERVED, List.of()));
        assertThat(available(mouse)).isEqualTo(8);
        assertThat(available(cable)).isZero();
    }

    @Test
    void takesNothingWhenOneLineIsShort() {
        var mouse = product(10);
        var monitor = product(1);
        var orderId = UUID.randomUUID();
        var lines = List.of(new ReservationLine(mouse, 2), new ReservationLine(monitor, 2));

        assertThatThrownBy(() -> reserve(orderId, lines))
                .isInstanceOfSatisfying(InsufficientStockException.class,
                        e -> assertThat(e.getMissingSkus()).containsExactly(monitor));

        assertThat(available(mouse)).isEqualTo(10);
        assertThat(available(monitor)).isEqualTo(1);
        assertThat(reservations.findById(orderId)).isEmpty();
    }

    @Test
    void storesRejectionWithMissingSkus() {
        var monitor = product(1);
        var orderId = UUID.randomUUID();

        var result = reject(orderId, List.of(new ReservationLine(monitor, 2)), List.of(monitor));

        assertThat(result).isEqualTo(new ReservationResult(orderId, ReservationStatus.REJECTED, List.of(monitor)));
        assertThat(reservations.findById(orderId)).get()
                .extracting(Reservation::result)
                .isEqualTo(result);
    }

    @Test
    void failsOnUnknownSkuWithoutTakingStock() {
        var mouse = product(10);
        var orderId = UUID.randomUUID();

        assertThatThrownBy(() -> reserve(orderId,
                List.of(new ReservationLine(mouse, 1), new ReservationLine("NO-SUCH-SKU", 1))))
                .isInstanceOf(UnknownSkuException.class);

        assertThat(available(mouse)).isEqualTo(10);
        assertThat(reservations.findById(orderId)).isEmpty();
    }

    @Test
    void lastUnitGoesToExactlyOneOfTwoConcurrentOrders() throws Exception {
        var monitor = product(1);
        var start = new CountDownLatch(1);
        List<CompletableFuture<String>> results;

        try (var pool = Executors.newFixedThreadPool(2)) {
            results = List.of(1, 2).stream()
                    .map(i -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return reserve(UUID.randomUUID(), List.of(new ReservationLine(monitor, 1))).status().name();
                    }, pool).exceptionally(e -> e.getCause().getClass().getSimpleName()))
                    .toList();
            start.countDown();
        }

        assertThat(results).extracting(CompletableFuture::join)
                .containsExactlyInAnyOrder("RESERVED", "InsufficientStockException");
        assertThat(available(monitor)).isZero();
    }

    private ReservationResult reserve(UUID orderId, List<ReservationLine> lines) {
        return transactions.execute(status -> service.reserve(orderId, lines));
    }

    private ReservationResult reject(UUID orderId, List<ReservationLine> lines, List<String> missingSkus) {
        return transactions.execute(status -> service.reject(orderId, lines, missingSkus));
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
