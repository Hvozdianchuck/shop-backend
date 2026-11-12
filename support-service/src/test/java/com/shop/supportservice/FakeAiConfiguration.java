package com.shop.supportservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class FakeAiConfiguration {

    // A question about BLIK is embedded next to the delivery sections, so vector search alone misses the payment rules.
    @Bean
    @Primary
    FakeEmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel()
                .similar("do you take blik", 0)
                .similar("delivery - ", 0);
    }
}
