package com.smartcheckout.risk.domain.rules;

import com.smartcheckout.risk.domain.RiskContext;
import com.smartcheckout.risk.domain.RiskReason;
import com.smartcheckout.risk.domain.RiskRule;
import com.smartcheckout.risk.domain.TransactionFacts;

import java.time.Duration;
import java.util.Optional;

/**
 * Many payments with the same card in a short time: typical of "card testing",
 * where a fraudster checks stolen cards with several small payments.
 */
public final class CardVelocityRule implements RiskRule {

    /** Window used by the application layer to count recent payments on the card. */
    public static final Duration WINDOW = Duration.ofMinutes(10);

    public static final int DEFAULT_WARNING_COUNT = 2;
    public static final int DEFAULT_BURST_COUNT = 4;

    private final int warningCount;
    private final int burstCount;

    public CardVelocityRule() {
        this(DEFAULT_WARNING_COUNT, DEFAULT_BURST_COUNT);
    }

    public CardVelocityRule(int warningCount, int burstCount) {
        this.warningCount = warningCount;
        this.burstCount = burstCount;
    }

    @Override
    public Optional<RiskReason> evaluate(TransactionFacts facts, RiskContext context) {
        int recent = context.recentPaymentsOnCard();
        String detail = recent + " other payments with the same card in the last " + WINDOW.toMinutes() + " minutes";
        if (recent >= burstCount) {
            return Optional.of(new RiskReason("CARD_TESTING", 45, detail));
        }
        if (recent >= warningCount) {
            return Optional.of(new RiskReason("CARD_VELOCITY", 25, detail));
        }
        return Optional.empty();
    }
}
