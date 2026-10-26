package com.smartcheckout.payment.application.port;

/**
 * Technical failure of the provider. Different from a decline: a decline is a final business answer,
 * an unavailability must be retried.
 */
public class PaymentProviderUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentProviderUnavailableException(String message) {
        super(message);
    }
}
