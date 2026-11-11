package com.shop.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("shop.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean relayEnabled,
        @DefaultValue("200ms") Duration pollInterval,
        @DefaultValue("100") int batchSize,
        @DefaultValue("10s") Duration sendTimeout,
        @DefaultValue("5s") Duration maxBackoff,
        @DefaultValue("3d") Duration publishedRetention,
        @DefaultValue("14d") Duration processedEventsRetention) {
}
