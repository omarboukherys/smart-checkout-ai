package com.smartcheckout.payment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One payment attempt for one checkout. The checkout id is the identity: there is never more
 * than one payment per checkout, which is what makes "pay twice" impossible on this side.
 */
public final class Payment {

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private final String checkoutId;
    private final long amountMinor;
    private final String currency;
    private final String paymentMethod;
    private final Instant createdAt;
    private PaymentStatus status;
    private String pspReference;
    private String declineCode;
    private String declineMessage;
    private Instant updatedAt;

    private Payment(String checkoutId, long amountMinor, String currency, String paymentMethod, PaymentStatus status,
                    String pspReference, String declineCode, String declineMessage, Instant createdAt,
                    Instant updatedAt) {
        this.checkoutId = Objects.requireNonNull(checkoutId, "checkoutId");
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.status = Objects.requireNonNull(status, "status");
        this.pspReference = pspReference;
        this.declineCode = declineCode;
        this.declineMessage = declineMessage;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Payment start(String checkoutId, long amountMinor, String currency, String paymentMethod,
                                Instant now) {
        if (amountMinor <= 0) {
            throw new DomainException("Payment amount must be positive");
        }
        if (currency == null || !CURRENCY.matcher(currency).matches()) {
            throw new DomainException("Currency must be an ISO 4217 code");
        }
        return new Payment(checkoutId, amountMinor, currency, paymentMethod, PaymentStatus.AUTHORIZING,
                null, null, null, now, now);
    }

    public static Payment rehydrate(String checkoutId, long amountMinor, String currency, String paymentMethod,
                                    PaymentStatus status, String pspReference, String declineCode,
                                    String declineMessage, Instant createdAt, Instant updatedAt) {
        return new Payment(checkoutId, amountMinor, currency, paymentMethod, status, pspReference, declineCode,
                declineMessage, createdAt, updatedAt);
    }

    /** Applies the provider's answer. Only possible once, while the payment is still being authorized. */
    public void complete(AuthorizationResult result, Instant at) {
        if (status != PaymentStatus.AUTHORIZING) {
            throw new DomainException("Payment " + checkoutId + " is already " + status);
        }
        switch (result) {
            case AuthorizationResult.Approved approved -> {
                this.status = PaymentStatus.AUTHORIZED;
                this.pspReference = approved.pspReference();
            }
            case AuthorizationResult.Declined declined -> {
                this.status = PaymentStatus.DECLINED;
                this.declineCode = declined.code();
                this.declineMessage = declined.message();
            }
        }
        this.updatedAt = at;
    }

    public boolean isCompleted() {
        return status != PaymentStatus.AUTHORIZING;
    }

    public String checkoutId() {
        return checkoutId;
    }

    public long amountMinor() {
        return amountMinor;
    }

    public String currency() {
        return currency;
    }

    public String paymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus status() {
        return status;
    }

    public String pspReference() {
        return pspReference;
    }

    public String declineCode() {
        return declineCode;
    }

    public String declineMessage() {
        return declineMessage;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
