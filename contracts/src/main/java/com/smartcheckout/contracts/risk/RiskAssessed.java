package com.smartcheckout.contracts.risk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The risk engine scored a checkout.
 *
 * @param decision APPROVE, REVIEW (held for a human, assisted by the AI agent) or BLOCK
 * @param score    0 to 100, the sum of the points of the rules that matched (capped)
 * @param reasons  every rule that matched, so the decision can always be explained
 */
public record RiskAssessed(
        UUID eventId,
        UUID checkoutId,
        Instant occurredAt,
        String decision,
        int score,
        List<Reason> reasons
) implements RiskEvent {

    public record Reason(String code, int points, String detail) {
    }
}
