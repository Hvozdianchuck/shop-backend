package com.shop.orderservice.api;

import com.shop.orderservice.TestcontainersConfiguration;
import com.shop.orderservice.application.PlaceOrderService;
import com.shop.orderservice.domain.Order;
import com.shop.orderservice.domain.OrderLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "resilience4j.ratelimiter.instances.placeOrder.limit-for-period=3",
        "resilience4j.ratelimiter.instances.placeOrder.limit-refresh-period=1m"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceOrderRateLimitTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    PlaceOrderService placeOrder;

    @Test
    void rejectsOrdersAboveLimitWith429() {
        when(placeOrder.place(anyList()))
                .thenAnswer(inv -> Order.place(List.of(new OrderLine("MOUSE-1", 1, new BigDecimal("24.90")))));

        for (int i = 0; i < 3; i++) {
            assertThat(post()).hasStatus(202);
        }
        var rejected = post();

        assertThat(rejected).hasStatus(429);
        assertThat(rejected).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private MockMvcTester.MockMvcRequestBuilder post() {
        return mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[{"sku":"MOUSE-1","quantity":1}]}""");
    }
}
