package com.smartcheckout.risk.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RiskEngineTest {

    private static final Instant NOW = Instant.parse("2026-10-26T14:00:00Z");

    private final RiskEngine engine = RiskEngine.withDefaultRules();

    @Test
    void a_small_store_payment_is_approved_without_any_reason() {
        RiskAssessment assessment = engine.assess(storePayment(11_430, "FR"), RiskContext.firstSeen(), NOW);

        assertThat(assessment.score()).isZero();
        assertThat(assessment.decision()).isEqualTo(RiskDecision.APPROVE);
        assertThat(assessment.reasons()).isEmpty();
    }

    @Test
    void a_high_amount_alone_stays_below_the_review_threshold() {
        RiskAssessment assessment = engine.assess(storePayment(600_000, "MA"), RiskContext.firstSeen(), NOW);

        assertThat(assessment.score()).isEqualTo(30);
        assertThat(assessment.decision()).isEqualTo(RiskDecision.APPROVE);
        assertThat(codes(assessment)).containsExactly("HIGH_AMOUNT");
    }

    @Test
    void a_high_online_amount_with_a_foreign_card_is_held_for_review() {
        RiskAssessment assessment = engine.assess(webPayment(899_900, "FR", "MA", "web-fp-1"),
                RiskContext.firstSeen(), NOW);

        assertThat(assessment.score()).isEqualTo(60);
        assertThat(assessment.decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(codes(assessment)).containsExactly("HIGH_AMOUNT", "GEO_MISMATCH");
    }

    @Test
    void a_very_high_online_amount_with_a_foreign_card_is_blocked() {
        RiskAssessment assessment = engine.assess(webPayment(2_500_000, "FR", "MA", "web-fp-1"),
                RiskContext.firstSeen(), NOW);

        assertThat(assessment.score()).isEqualTo(80);
        assertThat(assessment.decision()).isEqualTo(RiskDecision.BLOCK);
    }

    @Test
    void many_payments_on_the_same_card_look_like_card_testing() {
        RiskAssessment assessment = engine.assess(storePayment(1_000, "MA"), new RiskContext(5, false, false), NOW);

        assertThat(codes(assessment)).containsExactly("CARD_TESTING");
        assertThat(assessment.decision()).isEqualTo(RiskDecision.REVIEW);
    }

    @Test
    void a_foreign_card_in_store_is_not_suspicious() {
        RiskAssessment assessment = engine.assess(storePayment(11_430, "US"), RiskContext.firstSeen(), NOW);

        assertThat(assessment.reasons()).isEmpty();
    }

    @Test
    void a_new_device_is_only_suspicious_for_a_known_customer() {
        TransactionFacts payment = webPayment(11_430, "MA", "MA", "new-phone");

        assertThat(codes(engine.assess(payment, new RiskContext(0, true, false), NOW))).containsExactly("NEW_DEVICE");
        assertThat(codes(engine.assess(payment, new RiskContext(0, false, false), NOW))).isEmpty();
        assertThat(codes(engine.assess(payment, new RiskContext(0, true, true), NOW))).isEmpty();
    }

    @Test
    void the_score_is_capped_at_100() {
        RiskAssessment assessment = engine.assess(webPayment(2_500_000, "FR", "MA", "new-phone"),
                new RiskContext(6, true, false), NOW);

        assertThat(assessment.reasons()).hasSize(4);
        assertThat(assessment.score()).isEqualTo(RiskEngine.MAX_SCORE);
        assertThat(assessment.decision()).isEqualTo(RiskDecision.BLOCK);
    }

    @Test
    void a_new_rule_can_be_added_without_changing_the_engine() {
        RiskRule blockedCountry = (facts, context) -> "KP".equals(facts.cardCountry())
                ? Optional.of(new RiskReason("SANCTIONED_COUNTRY", 100, "Card from a sanctioned country"))
                : Optional.empty();
        RiskEngine customEngine = new RiskEngine(List.of(blockedCountry));

        RiskAssessment assessment = customEngine.assess(storePayment(1_000, "KP"), RiskContext.firstSeen(), NOW);

        assertThat(assessment.decision()).isEqualTo(RiskDecision.BLOCK);
    }

    private static TransactionFacts storePayment(long amountMinor, String cardCountry) {
        return new TransactionFacts("chk-1", "POS", null, amountMinor, "MAD", "tok_1", cardCountry, null,
                "POS-TERM-07", NOW);
    }

    private static TransactionFacts webPayment(long amountMinor, String cardCountry, String ipCountry, String device) {
        return new TransactionFacts("chk-2", "WEB", "C00042", amountMinor, "MAD", "tok_2", cardCountry, ipCountry,
                device, NOW);
    }

    private static List<String> codes(RiskAssessment assessment) {
        return assessment.reasons().stream().map(RiskReason::code).toList();
    }
}
