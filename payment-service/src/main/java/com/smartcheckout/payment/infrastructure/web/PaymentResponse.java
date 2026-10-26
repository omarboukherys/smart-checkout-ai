package com.smartcheckout.payment.infrastructure.web;

import com.smartcheckout.payment.domain.Payment;
import com.smartcheckout.payment.domain.PaymentStatus;

import java.time.Instant;

public record PaymentResponse(
        String checkoutId,
        PaymentStatus status,
        long amountMinor,
        String currency,
        String paymentMethod,
        String pspReference,
        String declineCode,
        String declineMessage,
        Instant createdAt,
        Instant updatedAt
) {

    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.checkoutId(), payment.status(), payment.amountMinor(), payment.currency(),
                payment.paymentMethod(), payment.pspReference(), payment.declineCode(), payment.declineMessage(),
                payment.createdAt(), payment.updatedAt());
    }
}
