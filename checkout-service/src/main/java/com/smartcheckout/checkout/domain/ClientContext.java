package com.smartcheckout.checkout.domain;

/**
 * Technical context of the payment request, used later by the risk engine.
 *
 * @param ipCountry ISO country of the customer's IP address (web and mobile), may be null
 * @param deviceId  payment terminal id (POS) or device fingerprint (web, mobile), may be null
 */
public record ClientContext(String ipCountry, String deviceId) {

    public static ClientContext none() {
        return new ClientContext(null, null);
    }
}
