package com.shop.supportservice.triage;

import com.shop.supportservice.tools.OrderLookupException;
import com.shop.supportservice.tools.OrderTools;
import com.shop.supportservice.tools.ToolJournal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.shop.supportservice.triage.TriageResults.policyAnswer;
import static org.assertj.core.api.Assertions.assertThat;

class HandoffPolicyTest {

    private final HandoffPolicy policy = new HandoffPolicy();
    private final ToolJournal journal = new ToolJournal();

    @Test
    void letsPlainAnswerThrough() {
        assertThat(policy.decide(policyAnswer(), List.of(), journal)).isEqualTo(new Handoff(false, null));
    }

    @ParameterizedTest
    @CsvSource({"COMPLAINT, complaint", "CANCEL_ORDER, cancellation needs confirmation"})
    void sendsComplaintsAndCancellationsToPersonEvenIfModelDoesNot(Intent intent, String reason) {
        var result = new TriageResult(intent, null, "en", "Summary.", "Draft.", List.of(), false);

        assertThat(policy.decide(result, List.of(), journal)).isEqualTo(new Handoff(true, reason));
    }

    @Test
    void sendsToPersonWhenChecksFailed() {
        assertThat(policy.decide(policyAnswer(), List.of("source x was not returned by searchPolicies"), journal).reason())
                .isEqualTo("assistant reply failed checks: source x was not returned by searchPolicies");
    }

    @Test
    void sendsToPersonWhenReplyCouldNotBeRead() {
        assertThat(policy.decide(null, List.of(), journal).needsHuman()).isTrue();
    }

    @Test
    void sendsToPersonWhenOrderDataWasUnavailable() {
        new OrderTools(id -> {
            throw new OrderLookupException(new RuntimeException());
        }, journal).getOrder("550e8400-e29b-41d4-a716-446655440000");

        assertThat(policy.decide(policyAnswer(), List.of(), journal).reason()).isEqualTo("order data unavailable");
    }

    @Test
    void followsModelSuggestion() {
        var result = new TriageResult(Intent.OTHER, null, "pl", "Summary.", "Draft.", List.of(), true);

        assertThat(policy.decide(result, List.of(), journal).reason()).isEqualTo("suggested by assistant");
    }
}
