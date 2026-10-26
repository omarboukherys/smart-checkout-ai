package com.smartcheckout.agent.domain;

import java.util.List;
import java.util.Objects;

/**
 * The agent's answer, validated: whatever the model returns, a report that reaches the supervisor
 * always has a recommendation, a confidence between 0 and 1, a summary and bounded lists.
 * An invalid answer is rejected and replaced by the rule-based fallback.
 */
public record InvestigationReport(
        Recommendation recommendation,
        double confidence,
        String summary,
        List<String> keyEvidence,
        List<String> questionsForCustomer
) {

    public static final int MAX_EVIDENCE = 5;
    public static final int MAX_QUESTIONS = 3;
    public static final int MAX_SUMMARY_LENGTH = 1_200;

    public InvestigationReport {
        if (recommendation == null) {
            throw new DomainException("The report has no recommendation");
        }
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new DomainException("Confidence must be between 0 and 1, got " + confidence);
        }
        if (summary == null || summary.isBlank()) {
            throw new DomainException("The report has no summary");
        }
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            throw new DomainException("The summary is too long");
        }
        keyEvidence = bounded(keyEvidence, MAX_EVIDENCE);
        questionsForCustomer = bounded(questionsForCustomer, MAX_QUESTIONS);
    }

    private static List<String> bounded(List<String> items, int max) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(item -> !item.isEmpty())
                .limit(max)
                .toList();
    }
}
