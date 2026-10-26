package com.smartcheckout.payment.application;

public record AuthorizePaymentCommand(
        String checkoutId,
        long amountMinor,
        String currency,
        String paymentMethod,
        String paymentToken
) {
}
