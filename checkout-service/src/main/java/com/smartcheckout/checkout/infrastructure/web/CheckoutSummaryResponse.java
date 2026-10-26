package com.smartcheckout.checkout.infrastructure.web;

import com.smartcheckout.checkout.application.CheckoutSummary;
import com.smartcheckout.checkout.domain.Channel;
import com.smartcheckout.checkout.domain.CheckoutStatus;

import java.time.Instant;
import java.util.UUID;

public record CheckoutSummaryResponse(
        UUID id,
        String storeId,
        Channel channel,
        CheckoutStatus status,
        long totalMinor,
        String total,
        String currency,
        Instant createdAt
) {

    static CheckoutSummaryResponse from(CheckoutSummary summary) {
        return new CheckoutSummaryResponse(
                summary.id(),
                summary.storeId(),
                summary.channel(),
                summary.status(),
                summary.totalMinor(),
                Amounts.format(summary.totalMinor()),
                summary.currency(),
                summary.createdAt());
    }
}
