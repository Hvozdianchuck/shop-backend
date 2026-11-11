package com.shop.orderservice.api;

import com.shop.orderservice.application.PlaceOrderService;
import com.shop.orderservice.application.UnknownProductsException;
import com.shop.orderservice.domain.CatalogUnavailableException;
import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderLine;
import com.shop.orderservice.domain.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    PlaceOrderService placeOrder;

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

    private MockMvcTester.MockMvcRequestBuilder post(String body) {
        return mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
