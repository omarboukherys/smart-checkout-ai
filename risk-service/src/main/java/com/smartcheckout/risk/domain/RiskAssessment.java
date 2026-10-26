package com.smartcheckout.risk.domain;

import java.time.Instant;
import java.util.List;

/**
 * Result of the risk engine for one checkout. The score is an integer from 0 to 100,
 * so the same inputs always give exactly the same decision (no floating point rounding).
 */
public record RiskAssessment(
        String checkoutId,
        int score,
        RiskDecision decision,
        List<RiskReason> reasons,
        Instant assessedAt
) {

    public RiskAssessment {
        reasons = List.copyOf(reasons);
    }
}
