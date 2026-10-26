package com.smartcheckout.checkout.domain;

/**
 * A business rule of the checkout was violated (invalid basket, raw card number, forbidden transition...).
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DomainException(String message) {
        super(message);
    }
}
