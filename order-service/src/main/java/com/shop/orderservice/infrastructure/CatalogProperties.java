package com.shop.orderservice.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("catalog")
public record CatalogProperties(URI baseUrl) {
}
