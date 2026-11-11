package com.shop.orderservice.infrastructure;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String ORDERS_TOPIC = "orders";

    @Bean
    NewTopic ordersTopic() {
        return TopicBuilder.name(ORDERS_TOPIC).partitions(3).replicas(1).build();
    }
}
