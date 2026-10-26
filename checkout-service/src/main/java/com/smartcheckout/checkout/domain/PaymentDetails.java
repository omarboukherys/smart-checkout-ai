package com.smartcheckout.checkout.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Payment instrument chosen by the customer, as returned by the PSP tokenization step.
 * <p>
 * The service never stores a card number (PAN): only the PSP token, the last 4 digits
 * and the issuing country. A value that looks like a PAN is refused, which keeps the
 * service out of the PCI-DSS cardholder data scope.
 */
public record PaymentDetails(PaymentMethod method, String token, String last4, String country) {

    private static final Pattern RAW_CARD_NUMBER = Pattern.compile("\\d{12,19}");
    private static final Pattern LAST_4 = Pattern.compile("\\d{4}");

    public PaymentDetails {
        Objects.requireNonNull(method, "method");
        if (token == null || token.isBlank()) {
            throw new DomainException("A payment token is required");
        }
        if (RAW_CARD_NUMBER.matcher(token.replaceAll("[\\s-]", "")).matches()) {
            throw new DomainException("Raw card numbers are refused: send the PSP token (PCI-DSS)");
        }
        if (method == PaymentMethod.CARD && (last4 == null || !LAST_4.matcher(last4).matches())) {
            throw new DomainException("Card payments require the last 4 digits of the card");
        }
    }
}
