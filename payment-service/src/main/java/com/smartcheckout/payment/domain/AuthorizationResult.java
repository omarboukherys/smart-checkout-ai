package com.smartcheckout.payment.domain;

/**
 * Answer of the payment provider. A sealed interface: code handling it must cover both cases.
 */
public sealed interface AuthorizationResult permits AuthorizationResult.Approved, AuthorizationResult.Declined {

    record Approved(String pspReference) implements AuthorizationResult {
    }

    /**
     * @param code stable code such as INSUFFICIENT_FUNDS, EXPIRED_CARD, LIMIT_EXCEEDED
     */
    record Declined(String code, String message) implements AuthorizationResult {
    }
}
