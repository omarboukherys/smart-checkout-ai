package com.smartcheckout.contracts.checkout;

import java.time.Instant;
import java.util.UUID;

/**
 * A customer asked to pay: the checkout is open and waits for risk assessment and payment.
 *
 * @param amountMinor amount in minor units (centimes), never a floating point value
 * @param cardToken   PSP token of the payment instrument; a raw card number never leaves the PSP
 * @param itemCount   total number of items in the basket
 */
public record CheckoutRequested(
        UUID eventId,
        UUID checkoutId,
        Instant occurredAt,
        String storeId,
        String channel,
        String customerId,
        long amountMinor,
        String currency,
        String paymentMethod,
        String cardToken,
        String cardLast4,
        String cardCountry,
        String ipCountry,
        String deviceId,
        int itemCount
) implements CheckoutEvent {
}
