package com.smartcheckout.risk.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * What the risk engine knows about the payment being scored, built from the CheckoutRequested event.
 */
public record TransactionFacts(
        String checkoutId,
        String channel,
        String customerId,
        long amountMinor,
        String currency,
        String cardToken,
        String cardCountry,
        String ipCountry,
        String deviceId,
        Instant occurredAt
) {

    public TransactionFacts {
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** Website and mobile app: the customer is not physically in the store. */
    public boolean isOnline() {
        return "WEB".equals(channel) || "MOBILE".equals(channel);
    }
}
