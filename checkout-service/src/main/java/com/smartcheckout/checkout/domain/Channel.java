package com.smartcheckout.checkout.domain;

/**
 * Sales channel of an omnichannel checkout.
 */
public enum Channel {

    /** In-store payment terminal: the customer may stay anonymous. */
    POS(false),
    /** E-commerce website: the customer is always identified. */
    WEB(true),
    /** Mobile application: the customer is always identified. */
    MOBILE(true);

    private final boolean requiresCustomerAccount;

    Channel(boolean requiresCustomerAccount) {
        this.requiresCustomerAccount = requiresCustomerAccount;
    }

    public boolean requiresCustomerAccount() {
        return requiresCustomerAccount;
    }
}
