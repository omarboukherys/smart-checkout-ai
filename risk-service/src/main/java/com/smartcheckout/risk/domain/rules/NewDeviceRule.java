package com.smartcheckout.risk.domain.rules;

import com.smartcheckout.risk.domain.RiskContext;
import com.smartcheckout.risk.domain.RiskReason;
import com.smartcheckout.risk.domain.RiskRule;
import com.smartcheckout.risk.domain.TransactionFacts;

import java.util.Optional;

/**
 * A known customer paying from a device never seen before: a possible account takeover.
 * A brand-new customer is not penalised (every device is new for them).
 */
public final class NewDeviceRule implements RiskRule {

    @Override
    public Optional<RiskReason> evaluate(TransactionFacts facts, RiskContext context) {
        if (facts.customerId() == null || facts.deviceId() == null) {
            return Optional.empty();
        }
        if (context.knownCustomer() && !context.knownDevice()) {
            return Optional.of(new RiskReason("NEW_DEVICE", 15,
                    "Known customer paying from a new device (" + facts.deviceId() + ")"));
        }
        return Optional.empty();
    }
}
