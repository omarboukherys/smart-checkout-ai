package com.smartcheckout.payment.domain;

public enum PaymentStatus {
    /** Recorded before calling the provider: if the service crashes, the call is retried with the same key. */
    AUTHORIZING,
    AUTHORIZED,
    DECLINED
}
