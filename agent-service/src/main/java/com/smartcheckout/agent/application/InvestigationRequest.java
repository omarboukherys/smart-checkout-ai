package com.smartcheckout.agent.application;

import java.util.List;

/**
 * What triggers an investigation: a checkout held by the risk engine, with its score and reason codes.
 */
public record InvestigationRequest(String checkoutId, int riskScore, List<String> reasonCodes) {

    public InvestigationRequest {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
