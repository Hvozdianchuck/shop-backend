package com.shop.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("shop.avro")
public record AvroProperties(List<String> trustedPackages) {

    public AvroProperties {
        trustedPackages = trustedPackages == null ? List.of() : List.copyOf(trustedPackages);
    }
}
