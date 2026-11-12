package com.shop.supportservice.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ToolsTest {

    private static final UUID ORDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final OrderSummary ORDER = new OrderSummary(ORDER_ID, "CONFIRMED", Instant.parse("2026-11-10T09:15:00Z"), 2);

    private final ToolJournal journal = new ToolJournal();

    @ParameterizedTest
    @ValueSource(strings = {"550e8400-e29b-41d4-a716-446655440000", "550E8400-E29B-41D4-A716-446655440000",
            "550e8400e29b41d4a716446655440000", " #550e8400-e29b-41d4-a716-446655440000 "})
    void acceptsOrderNumberInDifferentFormats(String orderId) {
        var tools = new OrderTools(id -> Optional.of(ORDER), journal);

        var info = tools.getOrder(orderId);

        assertThat(info).isEqualTo(new OrderTools.OrderInfo(ORDER_ID.toString(), "CONFIRMED", LocalDate.of(2026, 11, 10), 2, null));
        assertThat(journal.orders()).containsOnlyKeys(ORDER_ID);
    }

    @Test
    void rejectsInvalidOrderNumberWithoutLookup() {
        var tools = new OrderTools(id -> {
            throw new AssertionError("must not be called");
        }, journal);

        var info = tools.getOrder("12345");

        assertThat(info.error()).isEqualTo("Not a valid order number");
        assertThat(journal.calls()).containsExactly("getOrder");
        assertThat(journal.orders()).isEmpty();
    }

    @Test
    void reportsUnknownOrderWithoutRecordingIt() {
        var tools = new OrderTools(id -> Optional.empty(), journal);

        assertThat(tools.getOrder(ORDER_ID.toString()).error()).isEqualTo("Order not found");
        assertThat(journal.orders()).isEmpty();
    }

    @Test
    void tellsModelNotToGuessWhenOrderServiceIsDown() {
        var tools = new OrderTools(id -> {
            throw new OrderLookupException(new RuntimeException("connection refused"));
        }, journal);

        var info = tools.getOrder(ORDER_ID.toString());

        assertThat(new DefaultToolCallResultConverter().convert(info, OrderTools.OrderInfo.class))
                .isEqualTo("{\"orderId\":\"550e8400-e29b-41d4-a716-446655440000\","
                        + "\"error\":\"Order data is temporarily unavailable. Do not guess the status.\"}");
        assertThat(journal.isOrderLookupFailed()).isTrue();
    }

    @Test
    void recordsReturnedPolicySections() {
        var hits = List.of(new PolicyHit("returns#refund-time", "..."), new PolicyHit("payments#currency", "..."));
        var tools = new PolicyTools(query -> hits, journal);

        assertThat(tools.searchPolicies("refund")).isEqualTo(hits);
        assertThat(journal.sections()).containsExactly("returns#refund-time", "payments#currency");
    }
}
