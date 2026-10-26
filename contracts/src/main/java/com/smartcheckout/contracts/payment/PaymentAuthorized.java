package com.smartcheckout.contracts.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * The payment provider authorized the amount.
 *
 * @param pspReference the provider's transaction reference, needed later for capture or refund
 */
public record PaymentAuthorized(
        UUID eventId,
        UUID checkoutId,
        Instant occurredAt,
        String pspReference,
        long amountMinor,
        String currency
) implements PaymentEvent {
}
