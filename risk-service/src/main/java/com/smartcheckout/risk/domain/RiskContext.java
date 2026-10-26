package com.smartcheckout.risk.domain;

/**
 * History needed by the rules, computed by the application layer from past assessments.
 *
 * @param recentPaymentsOnCard payments with the same card in the velocity window, excluding the current one
 * @param knownCustomer        the customer already paid before
 * @param knownDevice          the customer already paid from this device
 */
public record RiskContext(int recentPaymentsOnCard, boolean knownCustomer, boolean knownDevice) {

    /** No history at all: first payment ever seen for this card and customer. */
    public static RiskContext firstSeen() {
        return new RiskContext(0, false, false);
    }
}
