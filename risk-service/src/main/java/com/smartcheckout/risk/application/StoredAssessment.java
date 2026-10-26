package com.smartcheckout.risk.application;

import com.smartcheckout.risk.domain.RiskAssessment;
import com.smartcheckout.risk.domain.TransactionFacts;

import java.util.UUID;

/**
 * An assessment as it is kept by the service.
 *
 * @param riskEventId   id of the RiskAssessed event, generated once: a replay republishes the same event id,
 *                      so downstream consumers can de-duplicate
 * @param sourceEventId id of the CheckoutRequested event that triggered the assessment
 */
public record StoredAssessment(UUID riskEventId, UUID sourceEventId, TransactionFacts facts, RiskAssessment assessment) {
}
