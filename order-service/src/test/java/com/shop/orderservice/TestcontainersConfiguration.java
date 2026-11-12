package com.shop.orderservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// Static so that every test context reuses the same containers instead of starting its own.
	static final Network network = Network.newNetwork();

	static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.4.0"))
			.withNetwork(network)
			.withListener("kafka:19092");

	static final GenericContainer<?> schemaRegistry = new GenericContainer<>(
			DockerImageName.parse("confluentinc/cp-schema-registry:8.2.5"))
			.withNetwork(network)
			.withExposedPorts(8081)
			.withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
			.withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
			.withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
			.dependsOn(kafka)
			.waitingFor(Wait.forHttp("/subjects").forStatusCode(200));

	static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.7-pg18"));

	public static String schemaRegistryUrl() {
		schemaRegistry.start();
		return "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
	}

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return kafka;
	}

	@Bean
	DynamicPropertyRegistrar schemaRegistryProperties() {
		return registry -> registry.add("spring.kafka.properties.schema.registry.url",
				TestcontainersConfiguration::schemaRegistryUrl);
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return postgres;
	}

}
