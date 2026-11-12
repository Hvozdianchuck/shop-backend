package com.shop.supportservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// Static so that every test context reuses the same database instead of starting its own.
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.7-pg18"));

	@Bean
	@ServiceConnection
	PostgreSQLContainer pgvectorContainer() {
		return postgres;
	}

}
