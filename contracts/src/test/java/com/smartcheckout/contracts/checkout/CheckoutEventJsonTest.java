package com.smartcheckout.contracts.checkout;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutEventJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializes_with_a_type_discriminator_and_reads_back_the_same_event() {
        CheckoutEvent event = new CheckoutRequested(
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-10-26T09:30:00Z"),
                "CASA-MAARIF-01", "POS", null,
                11_430L, "MAD",
                "CARD", "tok_visa_4242", "4242", "MA",
                "MA", "POS-TERM-07", 3);

        String json = mapper.writerFor(CheckoutEvent.class).writeValueAsString(event);
        CheckoutEvent readBack = mapper.readValue(json, CheckoutEvent.class);

        assertThat(json).contains("\"type\":\"CheckoutRequested\"");
        assertThat(readBack).isEqualTo(event);
    }
}
