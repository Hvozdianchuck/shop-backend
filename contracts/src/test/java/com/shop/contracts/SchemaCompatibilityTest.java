package com.shop.contracts;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class SchemaCompatibilityTest {

    private static final String SUBJECT = "orders-" + OrderPlaced.getClassSchema().getFullName();

    static Network network = Network.newNetwork();

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.4.0")
            .withNetwork(network)
            .withListener("kafka:19092");

    @Container
    static GenericContainer<?> schemaRegistry = new GenericContainer<>("confluentinc/cp-schema-registry:8.2.5")
            .withNetwork(network)
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
            .dependsOn(kafka)
            .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));

    static SchemaRegistryClient registry;

    @BeforeAll
    static void registerCurrentSchema() throws Exception {
        registry = new CachedSchemaRegistryClient(
                "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081), 10);
        registry.updateCompatibility(SUBJECT, "BACKWARD");
        registry.register(SUBJECT, new AvroSchema(OrderPlaced.getClassSchema()));
    }

    @Test
    void acceptsNewFieldWithDefault() throws Exception {
        var candidate = candidate("OrderPlaced-optional-field.avsc");

        assertThat(registry.testCompatibility(SUBJECT, candidate)).isTrue();
    }

    @Test
    void rejectsNewRequiredField() throws Exception {
        var candidate = candidate("OrderPlaced-required-field.avsc");

        assertThat(registry.testCompatibility(SUBJECT, candidate)).isFalse();
        assertThatThrownBy(() -> registry.register(SUBJECT, candidate))
                .isInstanceOf(RestClientException.class)
                .satisfies(e -> assertThat(((RestClientException) e).getStatus()).isEqualTo(409));
    }

    private static AvroSchema candidate(String file) throws IOException {
        try (InputStream in = SchemaCompatibilityTest.class.getResourceAsStream("/candidates/" + file)) {
            return new AvroSchema(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
