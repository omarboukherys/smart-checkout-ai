package com.smartcheckout.agent.infrastructure.ai;

import java.util.Optional;

/**
 * Everything the agent's tools are allowed to read. Read-only by design: the agent has no way
 * to approve, reject or modify anything. Each method returns the raw JSON of the service, or empty if unknown.
 */
public interface ReadOnlyServices {

    Optional<String> checkout(String checkoutId);

    Optional<String> riskAssessment(String checkoutId);

    Optional<String> customerHistory(String customerId, int limit);
}
