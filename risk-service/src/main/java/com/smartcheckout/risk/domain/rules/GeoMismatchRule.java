package com.smartcheckout.risk.domain.rules;

import com.smartcheckout.risk.domain.RiskContext;
import com.smartcheckout.risk.domain.RiskReason;
import com.smartcheckout.risk.domain.RiskRule;
import com.smartcheckout.risk.domain.TransactionFacts;

import java.util.Optional;

/**
 * Online payment where the card country differs from the IP country. Not applied in store:
 * a tourist paying with a foreign card at a terminal is a normal situation.
 */
public final class GeoMismatchRule implements RiskRule {

    @Override
    public Optional<RiskReason> evaluate(TransactionFacts facts, RiskContext context) {
        if (!facts.isOnline() || facts.cardCountry() == null || facts.ipCountry() == null) {
            return Optional.empty();
        }
        if (facts.cardCountry().equalsIgnoreCase(facts.ipCountry())) {
            return Optional.empty();
        }
        return Optional.of(new RiskReason("GEO_MISMATCH", 30,
                "Card issued in " + facts.cardCountry() + ", IP located in " + facts.ipCountry()));
    }
}
