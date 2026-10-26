package com.smartcheckout.checkout.application.port;

import com.smartcheckout.checkout.domain.Checkout;

/**
 * Output port: asks payment-service to authorize a checkout. Written to the outbox,
 * so the command is sent if and only if the transaction commits.
 */
public interface PaymentCommandPublisher {

    void requestAuthorization(Checkout checkout);
}
