package com.shop.orderservice.infrastructure;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.shop.contracts.OrderPlaced;
import com.shop.orderservice.TestcontainersConfiguration;
import com.shop.orderservice.application.PlaceOrderService;
import com.shop.orderservice.application.RequestedItem;
import com.shop.orderservice.domain.OrderEvents;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.shop.orderservice.KafkaTestClients.avroConsumerFromEnd;
import static com.shop.orderservice.KafkaTestClients.header;
import static com.shop.orderservice.KafkaTestClients.recordFor;
import static com.shop.orderservice.KafkaTestClients.recordsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderOutboxTest {

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
    JdbcClient jdbc;

    @Autowired
    PlaceOrderService placeOrder;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    KafkaContainer kafka;

    @MockitoSpyBean
    OrderEvents events;

    Consumer<String, Object> orders;

    @BeforeEach
    void readFromEndOfOrdersTopic() {
        catalog.stubFor(get(urlPathEqualTo("/api/products")).willReturn(okJson("""
                [{"sku":"MOUSE-1","name":"Mouse","price":24.90,"active":true}]""")));
        orders = avroConsumerFromEnd(KafkaConfig.ORDERS_TOPIC);
    }

    @AfterEach
    void close() {
        orders.close();
    }

    @Test
    void publishesOrderPlacedFromOutboxWithIdAndTypeHeaders() {
        var orderId = postOrder();

        var record = recordFor(orders, orderId);

        var outboxId = jdbc.sql("SELECT id FROM outbox WHERE aggregate_id = ?").param(orderId).query(UUID.class).single();
        assertThat(header(record, "eventId")).isEqualTo(outboxId.toString());
        assertThat(header(record, "eventType")).isEqualTo("OrderPlaced");
        var event = (OrderPlaced) record.value();
        assertThat(event.getOrderId()).hasToString(orderId);
        assertThat(event.getCurrency()).isEqualTo("EUR");
        assertThat(event.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getSku()).isEqualTo("MOUSE-1");
            assertThat(line.getQuantity()).isEqualTo(2);
            assertThat(line.getUnitPrice()).isEqualByComparingTo("24.90");
        });
    }

    @Test
    void publishesNothingWhenOrderIsNotPlaced() {
        catalog.stubFor(get(urlPathEqualTo("/api/products")).willReturn(serviceUnavailable()));

        assertThat(mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"items":[{"sku":"MOUSE-1","quantity":1}]}""")).hasStatus(503);

        assertThat(orders.poll(Duration.ofSeconds(3))).isEmpty();
    }

    @Test
    void rollsBackOrderAndEventTogetherWhenSavingFails() {
        long ordersBefore = count("orders");
        long outboxBefore = count("outbox");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Simulated failure after the event is written");
        }).when(events).orderPlaced(any());

        assertThatThrownBy(() -> placeOrder.place(List.of(new RequestedItem("MOUSE-1", 1))))
                .hasMessageContaining("Simulated failure");

        assertThat(count("orders")).isEqualTo(ordersBefore);
        assertThat(count("outbox")).isEqualTo(outboxBefore);
    }

    @Test
    void acceptsOrdersWhileKafkaIsDownAndSendsThemLater() {
        var docker = DockerClientFactory.instance().client();
        String orderId;
        docker.pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            orderId = postOrder();

            await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3))
                    .until(() -> meterRegistry.get("outbox.pending").gauge().value() >= 1);
        } finally {
            docker.unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        assertThat(recordsFor(orders, orderId, Duration.ofSeconds(45), 1)).hasSize(1);
        await().atMost(Duration.ofSeconds(5))
                .until(() -> meterRegistry.get("outbox.pending").gauge().value() == 0);
    }

    private String postOrder() {
        var response = mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"items":[{"sku":"MOUSE-1","quantity":2}]}""")
                .exchange();
        assertThat(response).hasStatus(202);
        return json.readTree(response.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
