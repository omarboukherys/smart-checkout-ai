package com.smartcheckout.contracts.risk;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RiskEventJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void round_trips_a_risk_assessment_with_its_reasons() {
        RiskEvent event = new RiskAssessed(
                UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-10-26T14:00:00Z"),
                "REVIEW", 60,
                List.of(new RiskAssessed.Reason("HIGH_AMOUNT", 30, "Amount 8999.00 MAD is above 5000.00 MAD"),
                        new RiskAssessed.Reason("GEO_MISMATCH", 30, "Card issued in FR, IP located in MA")));

        String json = mapper.writerFor(RiskEvent.class).writeValueAsString(event);
        RiskEvent readBack = mapper.readValue(json, RiskEvent.class);

        assertThat(json).contains("\"type\":\"RiskAssessed\"");
        assertThat(readBack).isEqualTo(event);
    }
}
