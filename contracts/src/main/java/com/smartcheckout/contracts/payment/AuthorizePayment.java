package com.smartcheckout.contracts.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Ask the payment provider to authorize the checkout amount on the customer's payment instrument.
 *
 * @param paymentToken PSP token, used for the call and never stored by payment-service
 */
public record AuthorizePayment(
        UUID commandId,
        UUID checkoutId,
        Instant issuedAt,
        long amountMinor,
        String currency,
        String paymentMethod,
        String paymentToken
) implements PaymentCommand {
}
