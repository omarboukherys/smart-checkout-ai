package com.smartcheckout.agent.infrastructure.ai;

import com.smartcheckout.agent.domain.DomainException;
import com.smartcheckout.agent.domain.InvestigationReport;
import com.smartcheckout.agent.domain.Recommendation;

import java.util.List;
import java.util.Locale;

/**
 * The JSON structure the model must return (Spring AI derives a JSON schema from this record).
 * Loose types on purpose (String, Double): a wrong value becomes a clear domain error
 * and the rule-based fallback, instead of an obscure parsing failure.
 */
public record AgentAnswer(
        String recommendation,
        Double confidence,
        String summary,
        List<String> keyEvidence,
        List<String> questionsForCustomer
) {

    /** Maps the answer to the domain, whose validation applies (confidence range, lengths, list sizes). */
    InvestigationReport toReport() {
        return new InvestigationReport(recommendation(recommendation),
                confidence == null ? Double.NaN : confidence, summary, keyEvidence, questionsForCustomer);
    }

    static InvestigationReport toReport(AgentAnswer answer) {
        if (answer == null) {
            throw new DomainException("The model returned no answer");
        }
        return answer.toReport();
    }

    private static Recommendation recommendation(String value) {
        if (value == null) {
            throw new DomainException("The report has no recommendation");
        }
        try {
            return Recommendation.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Unknown recommendation: " + value);
        }
    }
}
