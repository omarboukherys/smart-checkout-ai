package com.smartcheckout.risk.domain;

import com.smartcheckout.risk.domain.rules.CardVelocityRule;
import com.smartcheckout.risk.domain.rules.GeoMismatchRule;
import com.smartcheckout.risk.domain.rules.HighAmountRule;
import com.smartcheckout.risk.domain.rules.NewDeviceRule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Explainable scoring: the score is the sum of the points of the rules that matched, capped at 100.
 * Every decision comes with the list of reasons that produced it.
 */
public final class RiskEngine {

    public static final int MAX_SCORE = 100;

    private final List<RiskRule> rules;

    public RiskEngine(List<RiskRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static RiskEngine withDefaultRules() {
        return new RiskEngine(List.of(
                new HighAmountRule(),
                new CardVelocityRule(),
                new GeoMismatchRule(),
                new NewDeviceRule()));
    }

    public RiskAssessment assess(TransactionFacts facts, RiskContext context, Instant assessedAt) {
        List<RiskReason> reasons = rules.stream()
                .map(rule -> rule.evaluate(facts, context))
                .flatMap(Optional::stream)
                .toList();
        int score = Math.min(MAX_SCORE, reasons.stream().mapToInt(RiskReason::points).sum());
        return new RiskAssessment(facts.checkoutId(), score, RiskDecision.fromScore(score), reasons, assessedAt);
    }
}
