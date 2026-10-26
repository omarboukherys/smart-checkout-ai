package com.smartcheckout.agent.infrastructure.ai;

import com.smartcheckout.agent.application.InvestigationRequest;
import com.smartcheckout.agent.domain.DomainException;
import com.smartcheckout.agent.domain.InvestigationReport;
import com.smartcheckout.agent.domain.Recommendation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentAnswerTest {

    @Test
    void maps_a_valid_answer_to_the_domain_report() {
        AgentAnswer answer = new AgentAnswer(" escalate ", 0.65, "Carte FR, connexion depuis le Maroc.",
                List.of("Score 60", "Pays différents"), List.of("Êtes-vous en voyage ?"));

        InvestigationReport report = AgentAnswer.toReport(answer);

        assertThat(report.recommendation()).isEqualTo(Recommendation.ESCALATE);
        assertThat(report.confidence()).isEqualTo(0.65);
        assertThat(report.keyEvidence()).hasSize(2);
    }

    @Test
    void an_unknown_recommendation_is_a_domain_error() {
        AgentAnswer answer = new AgentAnswer("CANCEL_THE_PAYMENT", 0.9, "ok", null, null);

        assertThatThrownBy(() -> AgentAnswer.toReport(answer))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Unknown recommendation");
    }

    @Test
    void a_missing_confidence_or_an_empty_answer_is_a_domain_error() {
        assertThatThrownBy(() -> AgentAnswer.toReport(new AgentAnswer("REJECT", null, "ok", null, null)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> AgentAnswer.toReport(null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void the_prompt_contains_only_identifiers_and_codes() {
        String message = GeminiInvestigator.userMessage(
                new InvestigationRequest("chk-1", 60, List.of("HIGH_AMOUNT", "GEO_MISMATCH")));

        assertThat(message).contains("checkoutId: chk-1", "60 / 100", "HIGH_AMOUNT, GEO_MISMATCH");
    }
}
