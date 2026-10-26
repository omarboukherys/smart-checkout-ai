package com.smartcheckout.risk.domain.rules;

import com.smartcheckout.risk.domain.RiskContext;
import com.smartcheckout.risk.domain.RiskReason;
import com.smartcheckout.risk.domain.RiskRule;
import com.smartcheckout.risk.domain.TransactionFacts;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Large amounts carry more risk. Thresholds are in minor units of the checkout currency (MAD by default).
 */
public final class HighAmountRule implements RiskRule {

    public static final long DEFAULT_HIGH_MINOR = 500_000L;        // 5 000.00 MAD
    public static final long DEFAULT_VERY_HIGH_MINOR = 2_000_000L; // 20 000.00 MAD

    private final long highMinor;
    private final long veryHighMinor;

    public HighAmountRule() {
        this(DEFAULT_HIGH_MINOR, DEFAULT_VERY_HIGH_MINOR);
    }

    public HighAmountRule(long highMinor, long veryHighMinor) {
        this.highMinor = highMinor;
        this.veryHighMinor = veryHighMinor;
    }

    @Override
    public Optional<RiskReason> evaluate(TransactionFacts facts, RiskContext context) {
        if (facts.amountMinor() >= veryHighMinor) {
            return Optional.of(new RiskReason("VERY_HIGH_AMOUNT", 50,
                    "Amount " + format(facts.amountMinor(), facts) + " is above " + format(veryHighMinor, facts)));
        }
        if (facts.amountMinor() >= highMinor) {
            return Optional.of(new RiskReason("HIGH_AMOUNT", 30,
                    "Amount " + format(facts.amountMinor(), facts) + " is above " + format(highMinor, facts)));
        }
        return Optional.empty();
    }

    private static String format(long amountMinor, TransactionFacts facts) {
        return BigDecimal.valueOf(amountMinor, 2).toPlainString() + " " + facts.currency();
    }
}
