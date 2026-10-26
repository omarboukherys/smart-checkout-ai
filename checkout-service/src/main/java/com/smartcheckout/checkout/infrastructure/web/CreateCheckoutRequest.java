package com.smartcheckout.checkout.infrastructure.web;

import com.smartcheckout.checkout.application.OpenCheckoutCommand;
import com.smartcheckout.checkout.domain.Channel;
import com.smartcheckout.checkout.domain.CheckoutLine;
import com.smartcheckout.checkout.domain.ClientContext;
import com.smartcheckout.checkout.domain.PaymentDetails;
import com.smartcheckout.checkout.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * JSON body of {@code POST /api/v1/checkouts}. Bean Validation rejects malformed input (400);
 * business rules are checked by the domain (422).
 */
public record CreateCheckoutRequest(
        @NotBlank @Size(max = 64) String storeId,
        @NotNull Channel channel,
        @Size(max = 64) String customerId,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull LineRequest> lines,
        @NotNull @Valid PaymentRequest payment,
        @Valid ClientContextRequest context
) {

    public record LineRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 255) String label,
            @Positive @Max(10_000) int quantity,
            @Positive long unitPriceMinor
    ) {
    }

    public record PaymentRequest(
            @NotNull PaymentMethod method,
            @NotBlank @Size(max = 128) String token,
            @Pattern(regexp = "\\d{4}") String last4,
            @Pattern(regexp = "[A-Z]{2}") String country
    ) {
    }

    public record ClientContextRequest(
            @Pattern(regexp = "[A-Z]{2}") String ipCountry,
            @Size(max = 128) String deviceId
    ) {
    }

    OpenCheckoutCommand toCommand(String idempotencyKey) {
        List<CheckoutLine> domainLines = lines.stream()
                .map(line -> new CheckoutLine(line.sku(), line.label(), line.quantity(), line.unitPriceMinor()))
                .toList();
        PaymentDetails paymentDetails = new PaymentDetails(payment.method(), payment.token(),
                payment.last4(), payment.country());
        ClientContext clientContext = context == null
                ? ClientContext.none()
                : new ClientContext(context.ipCountry(), context.deviceId());
        return new OpenCheckoutCommand(idempotencyKey, storeId, channel, customerId, currency,
                domainLines, paymentDetails, clientContext);
    }
}
