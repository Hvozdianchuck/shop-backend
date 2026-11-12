package com.shop.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.avro.util.ClassSecurityValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionOperations;

@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties({OutboxProperties.class, AvroProperties.class})
public class MessagingAutoConfiguration {

    // Avro 1.12 only instantiates generated classes from trusted packages, both in the relay and in KafkaAvroDeserializer.
    MessagingAutoConfiguration(AvroProperties avro) {
        var packages = avro.trustedPackages();
        ClassSecurityValidator.setGlobal(ClassSecurityValidator.composite(
                ClassSecurityValidator.getGlobal(),
                type -> packages.contains(type.getPackageName())));
    }

    @Bean
    OutboxWriter outboxWriter(JdbcClient jdbc, ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        return new OutboxWriter(jdbc, tracer.getIfAvailable(() -> Tracer.NOOP),
                propagator.getIfAvailable(() -> Propagator.NOOP));
    }

    @Bean
    OutboxRelay outboxRelay(JdbcClient jdbc, TransactionOperations transactions, KafkaTemplate<String, Object> kafka,
                            OutboxProperties properties, ObjectProvider<Tracer> tracer,
                            ObjectProvider<Propagator> propagator, MeterRegistry meterRegistry) {
        Gauge.builder("outbox.pending", jdbc,
                        j -> j.sql("SELECT count(*) FROM outbox WHERE published_at IS NULL").query(Long.class).single())
                .register(meterRegistry);
        return new OutboxRelay(jdbc, transactions, kafka, properties, tracer.getIfAvailable(() -> Tracer.NOOP),
                propagator.getIfAvailable(() -> Propagator.NOOP), meterRegistry);
    }

    @Bean
    OutboxCleanup outboxCleanup(JdbcClient jdbc, OutboxProperties properties) {
        return new OutboxCleanup(jdbc, properties);
    }

    @Bean
    ProcessedEvents processedEvents(JdbcClient jdbc, MeterRegistry meterRegistry) {
        return new ProcessedEvents(jdbc, meterRegistry);
    }
}
