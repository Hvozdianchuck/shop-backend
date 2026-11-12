package com.shop.supportservice.triage;

import com.shop.supportservice.tools.ToolJournal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The model's answer is untrusted input: every order and policy it refers to must come from a tool call.
 */
@Component
public class ResultValidator {

    static final int MAX_SUMMARY = 300;
    static final int MAX_REPLY = 1500;
    private static final Set<String> LANGUAGES = Set.of("uk", "en", "pl");

    public List<String> validate(TriageResult result, ToolJournal journal) {
        var problems = new ArrayList<String>();
        if (result.intent() == null) {
            problems.add("intent is missing");
        }
        if (result.orderId() != null && !journal.orders().containsKey(parseUuid(result.orderId()))) {
            problems.add("order " + result.orderId() + " was not returned by getOrder");
        }
        result.sources().stream()
                .filter(source -> !journal.sections().contains(source))
                .forEach(source -> problems.add("source " + source + " was not returned by searchPolicies"));
        if (!LANGUAGES.contains(result.language())) {
            problems.add("unsupported language " + result.language());
        }
        if (result.summary() == null || result.summary().isBlank() || result.summary().length() > MAX_SUMMARY) {
            problems.add("summary is empty or longer than " + MAX_SUMMARY);
        }
        if (result.replyDraft() == null || result.replyDraft().isBlank() || result.replyDraft().length() > MAX_REPLY) {
            problems.add("reply draft is empty or longer than " + MAX_REPLY);
        }
        return problems;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
