package com.shop.orderservice.api;

import com.shop.orderservice.application.CancelOrderService;
import com.shop.orderservice.application.PlaceOrderService;
import com.shop.orderservice.application.UnknownOrderException;
import com.shop.orderservice.application.UnknownProductsException;
import com.shop.orderservice.domain.CatalogUnavailableException;
import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderLine;
import com.shop.orderservice.domain.OrderNotCancellableException;
import com.shop.orderservice.domain.OrderRepository;
import com.shop.orderservice.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    PlaceOrderService placeOrder;

    @MockitoBean
    CancelOrderService cancelOrder;

    @MockitoBean
    OrderRepository orders;

    @Test
    void acceptsOrderAndPointsToIt() {
        var order = Order.place(List.of(new OrderLine("MOUSE-1", 2, new BigDecimal("24.90"))));
        when(placeOrder.place(anyList())).thenReturn(order);

        var result = post("""
                {"items":[{"sku":"MOUSE-1","quantity":2}]}""");

        assertThat(result).hasStatus(202);
        assertThat(result).hasHeader("Location", "http://localhost/api/orders/" + order.getId());
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"items\":[]}",
            "{\"items\":[{\"sku\":\"MOUSE-1\",\"quantity\":0}]}",
            "{\"items\":[{\"sku\":\" \",\"quantity\":1}]}"
    })
    void rejectsInvalidRequest(String body) {
        assertThat(post(body)).hasStatus(400);
    }

    @Test
    void listsUnknownSkus() {
        when(placeOrder.place(anyList())).thenThrow(new UnknownProductsException(List.of("NOPE")));

        var result = post("""
                {"items":[{"sku":"NOPE","quantity":1}]}""");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.unknownSkus").asArray().containsExactly("NOPE");
    }

    @Test
    void asksToRetryLaterWhenCatalogIsUnavailable() {
        when(placeOrder.place(anyList())).thenThrow(new CatalogUnavailableException(new RuntimeException()));

        var result = post("""
                {"items":[{"sku":"MOUSE-1","quantity":1}]}""");

        assertThat(result).hasStatus(503);
        assertThat(result).hasHeader("Retry-After", "10");
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void returnsNotFoundForUnknownOrder() {
        var id = UUID.randomUUID();
        when(orders.findWithLinesById(id)).thenReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/orders/{id}", id)).hasStatus(404);
    }

    @Test
    void listsOrdersPageByPage() {
        var order = Order.place(List.of(new OrderLine("MOUSE-1", 1, new BigDecimal("24.90"))));
        when(orders.findPageWithLines(any())).thenReturn(new SliceImpl<>(List.of(order), PageRequest.of(1, 1), true));

        var result = mvc.get().uri("/api/orders?page=1&size=1");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.items[0].id").isEqualTo(order.getId().toString());
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101"})
    void rejectsInvalidPage(String query) {
        assertThat(mvc.get().uri("/api/orders?" + query)).hasStatus(400);
    }

    @Test
    void returnsCancelledOrder() {
        var order = Order.place(List.of(new OrderLine("MOUSE-1", 1, new BigDecimal("24.90"))));
        order.cancel();
        when(cancelOrder.cancel(order.getId())).thenReturn(order);

        var result = mvc.post().uri("/api/orders/{id}/cancel", order.getId());

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
    }

    @Test
    void refusesToCancelRejectedOrder() {
        var id = UUID.randomUUID();
        when(cancelOrder.cancel(id)).thenThrow(new OrderNotCancellableException(id, OrderStatus.REJECTED));

        assertThat(mvc.post().uri("/api/orders/{id}/cancel", id)).hasStatus(409);
    }

    @Test
    void reportsConcurrentChangeAsConflict() {
        var id = UUID.randomUUID();
        when(cancelOrder.cancel(id)).thenThrow(new ObjectOptimisticLockingFailureException(Order.class, id));

        assertThat(mvc.post().uri("/api/orders/{id}/cancel", id)).hasStatus(409);
    }

    @Test
    void returnsNotFoundWhenCancellingUnknownOrder() {
        var id = UUID.randomUUID();
        when(cancelOrder.cancel(id)).thenThrow(new UnknownOrderException(id));

        assertThat(mvc.post().uri("/api/orders/{id}/cancel", id)).hasStatus(404);
    }

    private MockMvcTester.MockMvcRequestBuilder post(String body) {
        return mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
