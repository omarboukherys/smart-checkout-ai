package com.smartcheckout.agent.infrastructure.web;

import com.smartcheckout.agent.domain.Investigation;
import com.smartcheckout.agent.domain.Recommendation;
import com.smartcheckout.agent.domain.ToolCall;

import java.time.Instant;
import java.util.List;

/**
 * What the back-office shows the supervisor. aiGenerated and fallbackReason make it explicit
 * whether the report comes from the model or from the rules.
 */
public record InvestigationResponse(
        String checkoutId,
        Recommendation recommendation,
        double confidence,
        String summary,
        List<String> keyEvidence,
        List<String> questionsForCustomer,
        int riskScore,
        List<String> riskReasons,
        boolean aiGenerated,
        String generatedBy,
        String fallbackReason,
        List<ToolCall> toolCalls,
        Instant startedAt,
        Instant completedAt,
        long durationMs
) {

    static InvestigationResponse from(Investigation investigation) {
        var report = investigation.report();
        return new InvestigationResponse(investigation.checkoutId(), report.recommendation(), report.confidence(),
                report.summary(), report.keyEvidence(), report.questionsForCustomer(), investigation.riskScore(),
                investigation.riskReasons(), investigation.byAi(), investigation.generatedBy(),
                investigation.fallbackReason(), investigation.toolCalls(), investigation.startedAt(),
                investigation.completedAt(), investigation.durationMs());
    }
}
