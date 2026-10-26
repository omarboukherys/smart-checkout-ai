package com.smartcheckout.contracts.checkout;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Events published by checkout-service on {@code checkout.events}.
 * <p>
 * The JSON carries a {@code type} discriminator, so consumers can deserialize to this
 * sealed interface and use an exhaustive {@code switch}. Field values such as channel
 * or payment method are plain strings: consumers follow the tolerant reader principle.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CheckoutRequested.class, name = "CheckoutRequested")
})
public sealed interface CheckoutEvent permits CheckoutRequested {

    /** Unique id of this event, used by consumers for de-duplication (at-least-once delivery). */
    UUID eventId();

    UUID checkoutId();

    Instant occurredAt();
}
