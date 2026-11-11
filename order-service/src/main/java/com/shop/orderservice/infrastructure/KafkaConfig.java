package com.shop.orderservice.infrastructure;

import com.shop.orderservice.application.UnknownOrderException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    public static final String ORDERS_TOPIC = "orders";
    public static final String STOCK_EVENTS_TOPIC = "stock-events";
    public static final String STOCK_EVENTS_DLT = "stock-events-dlt";

    @Bean
    NewTopic ordersTopic() {
        return TopicBuilder.name(ORDERS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic stockEventsDeadLetterTopic() {
        return TopicBuilder.name(STOCK_EVENTS_DLT).partitions(3).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template,
                                     DefaultKafkaProducerFactory<String, Object> producerFactory) {
        var bytesTemplate = new KafkaTemplate<>(producerFactory.copyWithConfigurationOverride(
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)));
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(Object.class, template);

        var backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2);
        backOff.setMaxInterval(4_000);

        var deadLetters = new DeadLetterPublishingRecoverer(templates);
        var handler = new DefaultErrorHandler((record, e) -> {
            log.warn("Record {}-{}@{} goes to the DLT: {}", record.topic(), record.partition(), record.offset(),
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            deadLetters.accept(record, e);
        }, backOff);
        handler.addNotRetryableExceptions(UnknownOrderException.class, UnsupportedEventException.class);
        return handler;
    }
}
