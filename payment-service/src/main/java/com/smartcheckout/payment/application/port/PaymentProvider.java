package com.smartcheckout.payment.application.port;

import com.smartcheckout.payment.domain.AuthorizationResult;

/**
 * Output port to the payment service provider (PSP). Implemented by a simulator in this project;
 * a real implementation would call the PSP's API.
 */
public interface PaymentProvider {

    /**
     * @throws PaymentProviderUnavailableException on a technical failure (timeout, 5xx): the caller retries
     */
    AuthorizationResult authorize(AuthorizationRequest request);
}
