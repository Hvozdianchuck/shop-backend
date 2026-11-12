package com.shop.supportservice.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the tools actually returned while one ticket was triaged. The model's answer is checked against it.
 */
public class ToolJournal {

    private final List<String> calls = new ArrayList<>();
    private final Map<UUID, OrderSummary> orders = new LinkedHashMap<>();
    private final Set<String> sections = new LinkedHashSet<>();
    private boolean orderLookupFailed;

    void called(String tool) {
        calls.add(tool);
    }

    void orderFound(OrderSummary order) {
        orders.put(order.id(), order);
    }

    void orderLookupFailed() {
        orderLookupFailed = true;
    }

    void sectionsReturned(List<PolicyHit> hits) {
        hits.forEach(hit -> sections.add(hit.section()));
    }

    public List<String> calls() {
        return Collections.unmodifiableList(calls);
    }

    public Map<UUID, OrderSummary> orders() {
        return Collections.unmodifiableMap(orders);
    }

    public Set<String> sections() {
        return Collections.unmodifiableSet(sections);
    }

    public boolean isOrderLookupFailed() {
        return orderLookupFailed;
    }
}
