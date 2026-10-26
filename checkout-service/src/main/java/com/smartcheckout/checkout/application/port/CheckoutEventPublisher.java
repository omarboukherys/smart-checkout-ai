package com.smartcheckout.checkout.application.port;

import com.smartcheckout.checkout.domain.Checkout;

/**
 * Output port: announces what happened to a checkout.
 * <p>
 * Must be called inside the same transaction as the state change. The adapter writes to an
 * outbox table, so the event is published if and only if the transaction commits.
 */
public interface CheckoutEventPublisher {

    void checkoutRequested(Checkout checkout);
}
