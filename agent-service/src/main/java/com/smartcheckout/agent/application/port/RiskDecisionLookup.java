package com.smartcheckout.agent.application.port;

import com.smartcheckout.agent.application.InvestigationRequest;

import java.util.Optional;

/**
 * Output port: reads the risk decision of a checkout, to run an investigation on demand.
 */
public interface RiskDecisionLookup {

    Optional<InvestigationRequest> find(String checkoutId);
}
