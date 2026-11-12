package com.shop.supportservice.triage;

import java.util.List;

public final class TriageResults {

    private TriageResults() {
    }

    public static TriageResult policyAnswer(String... sources) {
        return new TriageResult(Intent.POLICY_QUESTION, null, "en", "Asks about returns.",
                "You can return it within 14 days.", List.of(sources), false);
    }

    public static TriageResult orderStatus(String orderId) {
        return new TriageResult(Intent.ORDER_STATUS, orderId, "en", "Asks where the order is.",
                "Your order is confirmed.", List.of(), false);
    }

    public static String json(TriageResult r) {
        var sources = String.join(",", r.sources().stream().map(s -> "\"" + s + "\"").toList());
        return """
                {"intent":"%s","orderId":%s,"language":"%s","summary":"%s","replyDraft":"%s","sources":[%s],"needsHumanSuggested":%s}"""
                .formatted(r.intent(), r.orderId() == null ? "null" : "\"" + r.orderId() + "\"", r.language(),
                        r.summary(), r.replyDraft(), sources, r.needsHumanSuggested());
    }
}
