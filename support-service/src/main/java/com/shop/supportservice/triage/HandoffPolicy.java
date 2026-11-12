package com.shop.supportservice.triage;

import com.shop.supportservice.tools.ToolJournal;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The final word on whether a person must look at the ticket. The model only suggests;
 * complaints and cancellations go to a person whatever the model says.
 */
@Component
public class HandoffPolicy {

    public Handoff decide(TriageResult result, List<String> problems, ToolJournal journal) {
        if (result == null) {
            return Handoff.because("assistant reply could not be read");
        }
        if (!problems.isEmpty()) {
            return Handoff.because("assistant reply failed checks: " + String.join("; ", problems));
        }
        if (result.intent() == Intent.COMPLAINT) {
            return Handoff.because("complaint");
        }
        if (result.intent() == Intent.CANCEL_ORDER) {
            return Handoff.because("cancellation needs confirmation");
        }
        if (journal.isOrderLookupFailed()) {
            return Handoff.because("order data unavailable");
        }
        if (result.needsHumanSuggested()) {
            return Handoff.because("suggested by assistant");
        }
        return Handoff.none();
    }
}
