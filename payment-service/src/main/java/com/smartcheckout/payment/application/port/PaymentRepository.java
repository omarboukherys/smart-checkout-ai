package com.smartcheckout.payment.application.port;

import com.smartcheckout.payment.domain.Payment;

import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findByCheckoutId(String checkoutId);

    /** Fails if a payment already exists for the checkout. */
    void insert(Payment payment);

    void update(Payment payment);
}
