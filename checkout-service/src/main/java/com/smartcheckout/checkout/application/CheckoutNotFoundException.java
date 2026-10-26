package com.smartcheckout.checkout.application;

import java.util.UUID;

public class CheckoutNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CheckoutNotFoundException(UUID id) {
        super("Checkout " + id + " not found");
    }
}
