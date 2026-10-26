package com.smartcheckout.checkout.application;

import com.smartcheckout.checkout.domain.Channel;
import com.smartcheckout.checkout.domain.CheckoutStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model for lists: no basket lines, so listing does not load every line (no N+1 queries).
 */
public record CheckoutSummary(
        UUID id,
        String storeId,
        Channel channel,
        long totalMinor,
        String currency,
        CheckoutStatus status,
        Instant createdAt
) {
}
