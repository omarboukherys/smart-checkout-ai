package com.smartcheckout.contracts.payment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Commands sent by checkout-service to payment-service on {@code payment.commands}.
 * A command asks for something to be done (one recipient); an event states that something happened.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AuthorizePayment.class, name = "AuthorizePayment")
})
public sealed interface PaymentCommand permits AuthorizePayment {

    UUID commandId();

    UUID checkoutId();

    Instant issuedAt();
}
