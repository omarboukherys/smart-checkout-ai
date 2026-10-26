package com.smartcheckout.checkout.application;

import com.smartcheckout.checkout.domain.Checkout;

/**
 * @param created false when the request was a replay of an already processed Idempotency-Key
 */
public record OpenCheckoutResult(Checkout checkout, boolean created) {
}
