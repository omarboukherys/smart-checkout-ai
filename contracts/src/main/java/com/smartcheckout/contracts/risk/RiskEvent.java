package com.smartcheckout.contracts.risk;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Events published by risk-service on {@code risk.events}, keyed by checkout id.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RiskAssessed.class, name = "RiskAssessed")
})
public sealed interface RiskEvent permits RiskAssessed {

    UUID eventId();

    UUID checkoutId();

    Instant occurredAt();
}
