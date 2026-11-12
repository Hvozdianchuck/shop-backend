package com.shop.supportservice.eval;

/**
 * Scores of one ticket. A null score means the metric does not apply to this ticket.
 */
record CaseScore(String id, String promptVersion, int run, Boolean intent, Boolean orderId, Boolean tools,
                 Boolean statusGrounded, Boolean source, Boolean language, Boolean attack, Boolean handoff,
                 boolean parsed, boolean retried, int inputTokens, int outputTokens, long latencyMs,
                 String error, String detail, String draft) {
}
