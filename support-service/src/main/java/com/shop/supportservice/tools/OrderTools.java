package com.shop.supportservice.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;

public class OrderTools {

    static final String GET_ORDER = "getOrder";

    private static final Pattern UUID_FORMAT = Pattern.compile(
            "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");
    private static final Pattern UUID_WITHOUT_DASHES = Pattern.compile("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})");

    private final OrderLookup orders;
    private final ToolJournal journal;

    public OrderTools(OrderLookup orders, ToolJournal journal) {
        this.orders = orders;
        this.journal = journal;
    }

    @Tool(name = GET_ORDER, description = "Returns the status, creation date and number of items of a shop order. "
            + "Call it whenever the customer gives an order number.")
    public OrderInfo getOrder(@ToolParam(description = "Order number, a UUID like 550e8400-e29b-41d4-a716-446655440000")
                              String orderId) {
        journal.called(GET_ORDER);
        var id = parse(orderId);
        if (id == null) {
            return OrderInfo.error(orderId, "Not a valid order number");
        }
        try {
            return orders.find(id)
                    .map(order -> {
                        journal.orderFound(order);
                        return new OrderInfo(id.toString(), order.status(),
                                LocalDate.ofInstant(order.createdAt(), ZoneOffset.UTC), order.itemCount(), null);
                    })
                    .orElseGet(() -> OrderInfo.error(id.toString(), "Order not found"));
        } catch (OrderLookupException e) {
            journal.orderLookupFailed();
            return OrderInfo.error(id.toString(), "Order data is temporarily unavailable. Do not guess the status.");
        }
    }

    public static UUID parse(String orderId) {
        var value = orderId.strip().replaceFirst("^#", "");
        value = UUID_WITHOUT_DASHES.matcher(value).replaceFirst("$1-$2-$3-$4-$5");
        return UUID_FORMAT.matcher(value).matches() ? UUID.fromString(value) : null;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderInfo(String orderId, String status, LocalDate createdAt, Integer itemCount, String error) {

        static OrderInfo error(String orderId, String error) {
            return new OrderInfo(orderId, null, null, null, error);
        }
    }
}
