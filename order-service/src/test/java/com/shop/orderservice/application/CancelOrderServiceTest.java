package com.shop.orderservice.application;

import com.shop.orderservice.TestcontainersConfiguration;
import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderEvents;
import com.shop.orderservice.domain.OrderLine;
import com.shop.orderservice.domain.OrderRepository;
import com.shop.orderservice.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CancelOrderServiceTest {

    @Autowired
    CancelOrderService cancelOrder;

    @Autowired
    StockResultHandler stockResults;

    @Autowired
    OrderRepository orders;

    @Autowired
    JdbcClient jdbc;

    @MockitoSpyBean
    OrderEvents events;

    @Test
    void publishesOrderCancelledOnlyOnceForRepeatedRequest() {
        var orderId = pendingOrder();

        cancelOrder.cancel(orderId);
        var again = cancelOrder.cancel(orderId);

        assertThat(again.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelledEvents(orderId)).isEqualTo(1);
    }

    @Test
    void confirmationThatCommitsFirstWinsOverConcurrentCancel() throws Exception {
        var orderId = pendingOrder();
        var cancelInProgress = new CountDownLatch(1);
        var confirmed = new CountDownLatch(1);
        doAnswer(invocation -> {
            cancelInProgress.countDown();
            confirmed.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(events).orderCancelled(any());

        var cancel = CompletableFuture.runAsync(() -> cancelOrder.cancel(orderId));
        assertThat(cancelInProgress.await(10, TimeUnit.SECONDS)).isTrue();
        stockResults.stockReserved(orderId);
        confirmed.countDown();

        assertThatThrownBy(cancel::join).hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(cancelledEvents(orderId)).isZero();
    }

    private UUID pendingOrder() {
        return orders.save(Order.place(List.of(new OrderLine("MOUSE-1", 1, new BigDecimal("24.90"))))).getId();
    }

    private long cancelledEvents(UUID orderId) {
        return jdbc.sql("SELECT count(*) FROM outbox WHERE aggregate_id = ? AND event_type LIKE '%OrderCancelled'")
                .param(orderId.toString())
                .query(Long.class)
                .single();
    }
}
