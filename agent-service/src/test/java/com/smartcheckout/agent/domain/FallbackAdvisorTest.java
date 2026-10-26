package com.smartcheckout.agent.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackAdvisorTest {

    private final FallbackAdvisor advisor = new FallbackAdvisor();

    @Test
    void recommends_to_reject_card_testing() {
        InvestigationReport report = advisor.advise(45, List.of("CARD_TESTING"));

        assertThat(report.recommendation()).isEqualTo(Recommendation.REJECT);
        assertThat(report.keyEvidence()).anyMatch(evidence -> evidence.contains("10 minutes"));
    }

    @Test
    void escalates_a_country_mismatch_with_questions_for_the_customer() {
        InvestigationReport report = advisor.advise(60, List.of("HIGH_AMOUNT", "GEO_MISMATCH"));

        assertThat(report.recommendation()).isEqualTo(Recommendation.ESCALATE);
        assertThat(report.questionsForCustomer()).isNotEmpty();
        assertThat(report.keyEvidence()).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VERY_HIGH_AMOUNT", "NEW_DEVICE", "CARD_VELOCITY", "SOMETHING_NEW"})
    void never_recommends_to_approve(String code) {
        assertThat(advisor.advise(50, List.of(code)).recommendation()).isNotEqualTo(Recommendation.APPROVE);
    }
}
