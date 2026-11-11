package com.shop.orderservice.infrastructure;

import com.shop.orderservice.domain.CatalogProduct;
import com.shop.orderservice.domain.CatalogUnavailableException;
import com.shop.orderservice.domain.ProductCatalog;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProductCatalogClient implements ProductCatalog {

    private static final ParameterizedTypeReference<List<ProductResponse>> PRODUCT_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public ProductCatalogClient(RestClient.Builder builder, CatalogProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    @Retry(name = "catalog", fallbackMethod = "catalogUnavailable")
    @CircuitBreaker(name = "catalog")
    @Bulkhead(name = "catalog")
    public Map<String, CatalogProduct> findBySkus(Collection<String> skus) {
        var skuParam = skus.stream().sorted().collect(Collectors.joining(","));
        var products = restClient.get()
                .uri(uri -> uri.path("/api/products").queryParam("skus", skuParam).build())
                .retrieve()
                .body(PRODUCT_LIST);
        return products.stream()
                .map(p -> new CatalogProduct(p.sku(), p.price(), p.active()))
                .collect(Collectors.toMap(CatalogProduct::sku, Function.identity()));
    }

    private Map<String, CatalogProduct> catalogUnavailable(Collection<String> skus, Exception e) {
        throw new CatalogUnavailableException(e);
    }

    private record ProductResponse(String sku, BigDecimal price, boolean active) {
    }
}
