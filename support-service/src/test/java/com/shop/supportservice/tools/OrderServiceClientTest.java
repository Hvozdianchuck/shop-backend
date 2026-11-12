package com.shop.supportservice.tools;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.shop.supportservice.FakeAiConfiguration;
import com.shop.supportservice.TestcontainersConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeAiConfiguration.class})
class OrderServiceClientTest {

    private static final UUID ORDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @RegisterExtension
    static WireMockExtension orderService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void orderServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("order-service.base-url", orderService::baseUrl);
    }

    @Autowired
    OrderServiceClient client;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void closeCircuit() {
        circuitBreakers.circuitBreaker("orders").reset();
    }

    @Test
    void returnsStatusDateAndItemCount() {
        orderService.stubFor(get("/api/orders/" + ORDER_ID).willReturn(okJson("""
                {"id":"550e8400-e29b-41d4-a716-446655440000","status":"CONFIRMED","total":178.00,
                 "lines":[{"sku":"KBD-1","quantity":2,"unitPrice":89.00},{"sku":"MOUSE-1","quantity":1,"unitPrice":25.00}],
                 "createdAt":"2026-11-10T09:15:00Z","updatedAt":"2026-11-10T09:15:02Z"}""")));

        var order = client.find(ORDER_ID);

        assertThat(order).contains(new OrderSummary(ORDER_ID, "CONFIRMED", Instant.parse("2026-11-10T09:15:00Z"), 2));
    }

    @Test
    void returnsEmptyForUnknownOrderWithoutRetrying() {
        orderService.stubFor(get(anyUrl()).willReturn(notFound()));

        assertThat(client.find(ORDER_ID)).isEmpty();
        orderService.verify(1, getRequestedFor(urlEqualTo("/api/orders/" + ORDER_ID)));
    }

    @Test
    void retriesServerErrorsThenGivesUp() {
        orderService.stubFor(get(anyUrl()).willReturn(serviceUnavailable()));

        assertThatThrownBy(() -> client.find(ORDER_ID)).isInstanceOf(OrderLookupException.class);
        orderService.verify(3, getRequestedFor(urlEqualTo("/api/orders/" + ORDER_ID)));
    }

    @Test
    void timesOutSlowResponse() {
        orderService.stubFor(get(anyUrl()).willReturn(aResponse().withFixedDelay(3000)));
        var started = System.nanoTime();

        assertThatThrownBy(() -> client.find(ORDER_ID)).isInstanceOf(OrderLookupException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(9));
        orderService.verify(3, getRequestedFor(urlEqualTo("/api/orders/" + ORDER_ID)));
    }

    @Test
    void stopsCallingWhenCircuitIsOpen() {
        orderService.stubFor(get(anyUrl()).willReturn(serviceUnavailable()));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.find(ORDER_ID)).isInstanceOf(OrderLookupException.class);
        }
        assertThat(circuitBreakers.circuitBreaker("orders").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        orderService.resetRequests();

        assertThatThrownBy(() -> client.find(ORDER_ID)).isInstanceOf(OrderLookupException.class);
        orderService.verify(0, getRequestedFor(anyUrl()));
    }
}
