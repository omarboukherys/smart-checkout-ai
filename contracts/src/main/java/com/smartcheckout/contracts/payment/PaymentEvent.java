package com.smartcheckout.contracts.payment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Events published by payment-service on {@code payment.events}, keyed by checkout id.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PaymentAuthorized.class, name = "PaymentAuthorized"),
        @JsonSubTypes.Type(value = PaymentDeclined.class, name = "PaymentDeclined")
})
public sealed interface PaymentEvent permits PaymentAuthorized, PaymentDeclined {

    UUID eventId();

    UUID checkoutId();

    Instant occurredAt();
}
