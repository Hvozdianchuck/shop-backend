package com.shop.supportservice.triage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop.triage")
public record TriageProperties(String promptVersion) {
}
