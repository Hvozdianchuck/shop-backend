package com.shop.supportservice.tools;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderServiceClient implements OrderLookup {

    private final RestClient restClient;

    // The shared spring.http.clients timeouts are sized for the model, which may take two minutes to answer.
    public OrderServiceClient(RestClient.Builder builder, OrderServiceProperties properties) {
        var settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());
        this.restClient = builder.baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .build();
    }

    @Override
    @Retry(name = "orders", fallbackMethod = "unavailable")
    @CircuitBreaker(name = "orders")
    public Optional<OrderSummary> find(UUID orderId) {
        try {
            var order = restClient.get()
                    .uri("/api/orders/{id}", orderId)
                    .retrieve()
                    .body(OrderResponse.class);
            return Optional.of(new OrderSummary(order.id(), order.status(), order.createdAt(), order.lines().size()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    private Optional<OrderSummary> unavailable(UUID orderId, Exception e) {
        throw new OrderLookupException(e);
    }

    private record OrderResponse(UUID id, String status, Instant createdAt, List<Object> lines) {
    }
}
