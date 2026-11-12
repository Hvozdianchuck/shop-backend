package com.shop.supportservice.triage;

import com.shop.supportservice.tools.OrderTools;

import java.util.List;

public record TriageResult(Intent intent, String orderId, String language, String summary, String replyDraft,
                           List<String> sources, boolean needsHumanSuggested) {

    // Small models often write "" or omit the list where the schema says null or [], and copy the order number
    // in whatever form the customer typed it (upper case, without dashes).
    public TriageResult {
        orderId = orderId == null || orderId.isBlank() ? null : canonical(orderId.strip());
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    private static String canonical(String orderId) {
        var parsed = OrderTools.parse(orderId);
        return parsed == null ? orderId : parsed.toString();
    }
}
