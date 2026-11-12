package com.shop.supportservice.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("order-service")
public record OrderServiceProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
}
