package com.smartcheckout.payment.application.port;

import com.smartcheckout.payment.domain.Payment;

/**
 * Publishes the outcome of a completed payment (authorized or declined).
 */
public interface PaymentEventPublisher {

    void publish(Payment payment);
}
