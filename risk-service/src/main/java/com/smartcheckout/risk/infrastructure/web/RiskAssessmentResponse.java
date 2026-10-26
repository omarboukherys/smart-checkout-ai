package com.smartcheckout.risk.infrastructure.web;

import com.smartcheckout.risk.application.StoredAssessment;
import com.smartcheckout.risk.domain.RiskDecision;
import com.smartcheckout.risk.domain.RiskReason;

import java.time.Instant;
import java.util.List;

public record RiskAssessmentResponse(
        String checkoutId,
        int score,
        RiskDecision decision,
        List<RiskReason> reasons,
        String channel,
        String customerId,
        long amountMinor,
        String currency,
        String cardCountry,
        String ipCountry,
        String deviceId,
        Instant occurredAt,
        Instant assessedAt
) {

    static RiskAssessmentResponse from(StoredAssessment stored) {
        var facts = stored.facts();
        var assessment = stored.assessment();
        return new RiskAssessmentResponse(facts.checkoutId(), assessment.score(), assessment.decision(),
                assessment.reasons(), facts.channel(), facts.customerId(), facts.amountMinor(), facts.currency(),
                facts.cardCountry(), facts.ipCountry(), facts.deviceId(), facts.occurredAt(), assessment.assessedAt());
    }
}
