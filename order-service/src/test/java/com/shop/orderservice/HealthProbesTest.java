package com.shop.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

@SpringBootTest(properties = "management.endpoint.health.show-components=always")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthProbesTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void livenessDoesNotDependOnAnything() {
        assertThat(mvc.get().uri("/actuator/health/liveness")).bodyJson()
                .extractingPath("$.components").asInstanceOf(MAP)
                .containsOnlyKeys("livenessState");
    }

    @Test
    void readinessChecksDatabaseButNotKafka() {
        assertThat(mvc.get().uri("/actuator/health/readiness")).bodyJson()
                .extractingPath("$.components").asInstanceOf(MAP)
                .containsOnlyKeys("readinessState", "db");
    }
}
