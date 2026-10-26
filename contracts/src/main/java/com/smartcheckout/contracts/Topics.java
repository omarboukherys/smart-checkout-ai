package com.smartcheckout.contracts;

/**
 * Kafka topic names. Each topic carries the events of one bounded context,
 * keyed by checkout id so that all events of a checkout stay ordered.
 */
public final class Topics {

    public static final String CHECKOUT_EVENTS = "checkout.events";
    public static final String RISK_EVENTS = "risk.events";
    public static final String PAYMENT_EVENTS = "payment.events";

    private Topics() {
    }
}
