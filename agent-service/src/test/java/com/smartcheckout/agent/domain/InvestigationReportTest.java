package com.smartcheckout.agent.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The model's answer is untrusted: these rules decide whether it can reach the supervisor.
 */
class InvestigationReportTest {

    @Test
    void keeps_a_valid_answer_and_bounds_the_lists() {
        InvestigationReport report = new InvestigationReport(Recommendation.ESCALATE, 0.7, "Résumé",
                List.of("a", " ", "b", "c", "d", "e", "f"), List.of("q1", "q2", "q3", "q4"));

        assertThat(report.keyEvidence()).containsExactly("a", "b", "c", "d", "e");
        assertThat(report.questionsForCustomer()).hasSize(InvestigationReport.MAX_QUESTIONS);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.5, Double.NaN})
    void refuses_a_confidence_outside_0_and_1(double confidence) {
        assertThatThrownBy(() -> new InvestigationReport(Recommendation.APPROVE, confidence, "Résumé", null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void refuses_an_answer_without_recommendation_or_summary() {
        assertThatThrownBy(() -> new InvestigationReport(null, 0.5, "Résumé", null, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new InvestigationReport(Recommendation.REJECT, 0.5, " ", null, null))
                .isInstanceOf(DomainException.class);
    }
}
