package com.shop.supportservice.triage;

import com.shop.supportservice.tools.OrderLookup;
import com.shop.supportservice.tools.OrderSummary;
import com.shop.supportservice.tools.OrderTools;
import com.shop.supportservice.tools.PolicyHit;
import com.shop.supportservice.tools.PolicyTools;
import com.shop.supportservice.tools.ToolJournal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.shop.supportservice.triage.TriageResults.orderStatus;
import static com.shop.supportservice.triage.TriageResults.policyAnswer;
import static org.assertj.core.api.Assertions.assertThat;

class ResultValidatorTest {

    private static final UUID ORDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private final ResultValidator validator = new ResultValidator();
    private final ToolJournal journal = new ToolJournal();

    @Test
    void acceptsOrderThatToolReturned() {
        OrderLookup lookup = id -> Optional.of(new OrderSummary(ORDER_ID, "CONFIRMED", Instant.now(), 1));
        new OrderTools(lookup, journal).getOrder(ORDER_ID.toString());

        assertThat(validator.validate(orderStatus(ORDER_ID.toString()), journal)).isEmpty();
    }

    @Test
    void rejectsOrderTheModelMadeUp() {
        assertThat(validator.validate(orderStatus(ORDER_ID.toString()), journal))
                .containsExactly("order " + ORDER_ID + " was not returned by getOrder");
    }

    @Test
    void rejectsOrderThatWasLookedUpButNotFound() {
        new OrderTools(id -> Optional.empty(), journal).getOrder(ORDER_ID.toString());

        assertThat(validator.validate(orderStatus(ORDER_ID.toString()), journal)).hasSize(1);
    }

    @Test
    void rejectsSourceThatSearchDidNotReturn() {
        new PolicyTools(query -> List.of(new PolicyHit("returns#return-window", "...")), journal).searchPolicies("return");

        assertThat(validator.validate(policyAnswer("returns#return-window", "returns#refund-forever"), journal))
                .containsExactly("source returns#refund-forever was not returned by searchPolicies");
    }

    @Test
    void rejectsUnsupportedLanguageAndOverlongDraft() {
        var result = new TriageResult(Intent.OTHER, null, "de", "Summary.", "x".repeat(1501), List.of(), false);

        assertThat(validator.validate(result, journal)).hasSize(2);
    }
}
