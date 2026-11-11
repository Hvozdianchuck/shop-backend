package com.shop.orderservice.infrastructure;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.shop.orderservice.TestcontainersConfiguration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderPlacedPublishingTest {

    @RegisterExtension
    static WireMockExtension catalog = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void catalogUrl(DynamicPropertyRegistry registry) {
        registry.add("catalog.base-url", catalog::baseUrl);
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    KafkaConnectionDetails kafka;

    Consumer<String, String> consumer;

    @BeforeEach
    void readFromEndOfOrdersTopic() {
        consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                new StringDeserializer(), new StringDeserializer());
        var partitions = consumer.partitionsFor("orders").stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void publishesOrderPlacedKeyedByOrderId() {
        catalog.stubFor(get(urlPathEqualTo("/api/products")).willReturn(okJson("""
                [{"sku":"MOUSE-1","name":"Mouse","price":24.90,"active":true}]""")));

        var response = mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"items":[{"sku":"MOUSE-1","quantity":2}]}""")
                .exchange();
        assertThat(response).hasStatus(202);
        var orderId = json.readTree(response.getResponse().getContentAsByteArray()).get("id").asString();

        var record = KafkaTestUtils.getSingleRecord(consumer, "orders", Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo(orderId);
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
        var event = json.readTree(record.value());
        assertThat(event.get("eventType").asString()).isEqualTo("OrderPlaced");
        assertThat(event.get("orderId").asString()).isEqualTo(orderId);
        assertThat(event.get("eventId").asString()).isNotBlank();
        assertThat(event.at("/lines/0/sku").asString()).isEqualTo("MOUSE-1");
        assertThat(event.at("/lines/0/quantity").asInt()).isEqualTo(2);
    }

    @Test
    void publishesNothingWhenOrderIsNotPlaced() {
        catalog.stubFor(get(urlPathEqualTo("/api/products")).willReturn(serviceUnavailable()));

        assertThat(mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[{"sku":"MOUSE-1","quantity":1}]}""")).hasStatus(503);

        assertThat(KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(3)).count()).isZero();
    }
}
