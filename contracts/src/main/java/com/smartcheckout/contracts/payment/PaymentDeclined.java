package com.smartcheckout.contracts.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * The payment provider refused the payment.
 *
 * @param reasonCode stable code such as INSUFFICIENT_FUNDS, EXPIRED_CARD, LIMIT_EXCEEDED
 */
public record PaymentDeclined(
        UUID eventId,
        UUID checkoutId,
        Instant occurredAt,
        String reasonCode,
        String reasonMessage
) implements PaymentEvent {
}
