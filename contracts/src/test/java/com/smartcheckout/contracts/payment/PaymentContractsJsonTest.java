package com.smartcheckout.contracts.payment;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentContractsJsonTest {

    private static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void round_trips_the_authorize_payment_command() {
        PaymentCommand command = new AuthorizePayment(UUID.randomUUID(), UUID.randomUUID(), NOW,
                11_430L, "MAD", "CARD", "tok_visa_4242");

        String json = mapper.writerFor(PaymentCommand.class).writeValueAsString(command);

        assertThat(json).contains("\"type\":\"AuthorizePayment\"");
        assertThat(mapper.readValue(json, PaymentCommand.class)).isEqualTo(command);
    }

    @Test
    void round_trips_both_payment_outcomes() {
        PaymentEvent authorized = new PaymentAuthorized(UUID.randomUUID(), UUID.randomUUID(), NOW,
                "psp_123", 11_430L, "MAD");
        PaymentEvent declined = new PaymentDeclined(UUID.randomUUID(), UUID.randomUUID(), NOW,
                "INSUFFICIENT_FUNDS", "Insufficient funds");

        for (PaymentEvent event : new PaymentEvent[]{authorized, declined}) {
            String json = mapper.writerFor(PaymentEvent.class).writeValueAsString(event);
            assertThat(mapper.readValue(json, PaymentEvent.class)).isEqualTo(event);
        }
    }
}
