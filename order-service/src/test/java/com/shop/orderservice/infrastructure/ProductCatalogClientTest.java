package com.shop.orderservice.infrastructure;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.shop.orderservice.TestcontainersConfiguration;
import com.shop.orderservice.domain.CatalogUnavailableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProductCatalogClientTest {

    @RegisterExtension
    static WireMockExtension catalog = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().containerThreads(40))
            .build();

    @DynamicPropertySource
    static void catalogUrl(DynamicPropertyRegistry registry) {
        registry.add("catalog.base-url", catalog::baseUrl);
    }

    @Autowired
    ProductCatalogClient client;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void closeCircuit() {
        circuitBreakers.circuitBreaker("catalog").reset();
    }

    @Test
    void fetchesAllSkusInOneRequest() {
        catalog.stubFor(get(urlPathEqualTo("/api/products")).willReturn(okJson("""
                [{"sku":"KBD-1","name":"Keyboard","price":89.00,"active":true},
                 {"sku":"CAM-720","name":"Webcam","price":19.00,"active":false}]""")));

        var products = client.findBySkus(List.of("KBD-1", "CAM-720", "NOPE"));

        assertThat(products).containsOnlyKeys("KBD-1", "CAM-720");
        assertThat(products.get("KBD-1").price()).isEqualByComparingTo("89.00");
        assertThat(products.get("CAM-720").active()).isFalse();
        catalog.verify(1, getRequestedFor(urlPathEqualTo("/api/products"))
                .withQueryParam("skus", equalTo("CAM-720,KBD-1,NOPE")));
    }

    static Stream<Arguments> failedResponses() {
        return Stream.of(
                arguments(serviceUnavailable(), HttpServerErrorException.ServiceUnavailable.class, 3),
                arguments(okJson("[]").withFixedDelay(3_000), ResourceAccessException.class, 3),
                arguments(aResponse().withStatus(404), HttpClientErrorException.NotFound.class, 1));
    }

    @ParameterizedTest
    @MethodSource("failedResponses")
    void retriesOnlyServerErrorsAndTimeouts(ResponseDefinitionBuilder response, Class<? extends Exception> cause,
                                            int expectedAttempts) {
        catalog.stubFor(get(urlPathEqualTo("/api/products")).willReturn(response));

        assertThatThrownBy(() -> client.findBySkus(List.of("KBD-1")))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasCauseInstanceOf(cause);

        catalog.verify(expectedAttempts, getRequestedFor(urlPathEqualTo("/api/products")));
    }

    @Test
    void clientErrorsDoNotOpenCircuit() {
        catalog.stubFor(get(urlPathEqualTo("/api/products")).willReturn(aResponse().withStatus(404)));

        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> client.findBySkus(List.of("KBD-1"))).isInstanceOf(CatalogUnavailableException.class);
        }

        var circuit = circuitBreakers.circuitBreaker("catalog");
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void openCircuitFailsFastWithoutCallingCatalog() {
        catalog.stubFor(get(urlPathEqualTo("/api/products")).willReturn(serviceUnavailable()));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> client.findBySkus(List.of("KBD-1"))).isInstanceOf(CatalogUnavailableException.class);
        }
        assertThat(circuitBreakers.circuitBreaker("catalog").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsBefore = catalog.getAllServeEvents().size();

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.findBySkus(List.of("KBD-1")))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(100));
        assertThat(catalog.getAllServeEvents()).hasSize(callsBefore);
    }

    @Test
    void rejectsCallsAboveBulkheadLimit() throws Exception {
        catalog.stubFor(get(urlPathEqualTo("/api/products")).willReturn(okJson("[]")));
        client.findBySkus(List.of("KBD-1"));
        catalog.stubFor(get(urlPathEqualTo("/api/products")).willReturn(okJson("[]").withFixedDelay(1_500)));
        var start = new CountDownLatch(1);
        var calls = new ArrayList<CompletableFuture<Throwable>>();

        try (var pool = Executors.newFixedThreadPool(25)) {
            for (int i = 0; i < 25; i++) {
                calls.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                        client.findBySkus(List.of("KBD-1"));
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                }, pool));
            }
            start.countDown();
        }

        var failures = calls.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
        assertThat(failures).hasSize(5).allSatisfy(e -> assertThat(e)
                .isInstanceOf(CatalogUnavailableException.class)
                .hasCauseInstanceOf(BulkheadFullException.class));
    }
}
