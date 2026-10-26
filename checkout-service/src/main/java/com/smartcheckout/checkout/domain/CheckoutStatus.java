package com.smartcheckout.checkout.domain;

public enum CheckoutStatus {
    /** Waiting for risk assessment and payment authorization. */
    PENDING_PAYMENT,
    /** Suspicious payment parked for a human review. */
    ON_HOLD,
    PAID,
    /** Refused by the payment provider (insufficient funds, expired card...). */
    DECLINED,
    /** Refused by the risk engine or by a reviewer. */
    REJECTED,
    CANCELLED
}
