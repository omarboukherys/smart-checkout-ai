package com.smartcheckout.payment.application.port;

/**
 * @param idempotencyKey sent to the provider: the same key always returns the same answer,
 *                       so retrying after a crash or a timeout never charges the customer twice
 */
public record AuthorizationRequest(String idempotencyKey, long amountMinor, String currency, String paymentToken) {
}
