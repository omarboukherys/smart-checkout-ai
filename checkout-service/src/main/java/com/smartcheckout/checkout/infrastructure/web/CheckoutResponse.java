package com.smartcheckout.checkout.infrastructure.web;

import com.smartcheckout.checkout.domain.Channel;
import com.smartcheckout.checkout.domain.Checkout;
import com.smartcheckout.checkout.domain.CheckoutStatus;
import com.smartcheckout.checkout.domain.PaymentMethod;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public view of a checkout. The payment token is never returned: only the last 4 digits.
 */
public record CheckoutResponse(
        UUID id,
        String storeId,
        Channel channel,
        String customerId,
        CheckoutStatus status,
        String statusReason,
        long totalMinor,
        String total,
        String currency,
        PaymentMethod paymentMethod,
        String cardLast4,
        int itemCount,
        List<LineResponse> lines,
        Instant createdAt,
        Instant updatedAt
) {

    public record LineResponse(String sku, String label, int quantity, long unitPriceMinor) {
    }

    static CheckoutResponse from(Checkout checkout) {
        List<LineResponse> lines = checkout.lines().stream()
                .map(line -> new LineResponse(line.sku(), line.label(), line.quantity(), line.unitPriceMinor()))
                .toList();
        return new CheckoutResponse(
                checkout.id(),
                checkout.storeId(),
                checkout.channel(),
                checkout.customerId(),
                checkout.status(),
                checkout.statusReason(),
                checkout.totalMinor(),
                Amounts.format(checkout.totalMinor()),
                checkout.currency(),
                checkout.payment().method(),
                checkout.payment().last4(),
                checkout.itemCount(),
                lines,
                checkout.createdAt(),
                checkout.updatedAt());
    }
}
