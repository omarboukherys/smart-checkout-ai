package com.smartcheckout.agent;

import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.risk.RiskAssessed;
import com.smartcheckout.contracts.risk.RiskEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole service with a real MongoDB and a real Kafka, without AI: the CI has no API key, and a test
 * must not depend on a paid, non-deterministic external model. The AI path is covered by unit tests.
 */
@SpringBootTest(properties = {
        // Explicit, so a local .env with a real key never turns the AI on during the build.
        "spring.ai.model.chat=none",
        // The listener must also read the events sent before its partitions were assigned.
        "spring.kafka.consumer.auto-offset-reset=earliest",
        // Nothing listens on port 1: the other services are "down".
        "smartcheckout.services.risk-url=http://localhost:1",
        "smartcheckout.services.checkout-url=http://localhost:1",
        "smartcheckout.http.timeout-ms=500"
})
@AutoConfigureMockMvc
class AgentServiceIntegrationTest {

    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        MONGO.start();
        KAFKA.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void a_payment_held_for_review_gets_a_report_even_without_ai() throws Exception {
        UUID checkoutId = UUID.randomUUID();

        send(checkoutId.toString(), riskAssessed(checkoutId, "REVIEW", 60, "HIGH_AMOUNT", "GEO_MISMATCH"));

        awaitInvestigation(checkoutId);
        mockMvc.perform(get("/api/v1/investigations/{id}", checkoutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").value("ESCALATE"))
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andExpect(jsonPath("$.generatedBy").value("rules"))
                .andExpect(jsonPath("$.fallbackReason").value("AI disabled: no API key configured"))
                .andExpect(jsonPath("$.riskScore").value(60))
                .andExpect(jsonPath("$.questionsForCustomer").isNotEmpty());
    }

    @Test
    void approved_payments_are_not_investigated() throws Exception {
        UUID approved = UUID.randomUUID();
        UUID held = UUID.randomUUID();
        // Same Kafka key = same partition = processed in order: once "held" is investigated,
        // "approved" has necessarily been read before it.
        String key = UUID.randomUUID().toString();

        send(key, riskAssessed(approved, "APPROVE", 10));
        send(key, riskAssessed(held, "REVIEW", 45, "CARD_TESTING"));

        awaitInvestigation(held);
        mockMvc.perform(get("/api/v1/investigations/{id}", approved)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/investigations/{id}", held))
                .andExpect(jsonPath("$.recommendation").value("REJECT"));
    }

    @Test
    void asking_for_a_new_investigation_while_the_risk_service_is_down_returns_503() throws Exception {
        mockMvc.perform(post("/api/v1/investigations/{id}", UUID.randomUUID()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void an_unknown_checkout_has_no_investigation() throws Exception {
        mockMvc.perform(get("/api/v1/investigations/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    private static RiskAssessed riskAssessed(UUID checkoutId, String decision, int score, String... codes) {
        List<RiskAssessed.Reason> reasons = Arrays.stream(codes)
                .map(code -> new RiskAssessed.Reason(code, 30, code))
                .toList();
        return new RiskAssessed(UUID.randomUUID(), checkoutId, Instant.now(), decision, score, reasons);
    }

    private void send(String key, RiskAssessed event) throws Exception {
        String payload = jsonMapper.writerFor(RiskEvent.class).writeValueAsString(event);
        kafkaTemplate.send(Topics.RISK_EVENTS, key, payload).get();
    }

    private void awaitInvestigation(UUID checkoutId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        int status = 404;
        while (System.nanoTime() < deadline && status != 200) {
            status = mockMvc.perform(get("/api/v1/investigations/{id}", checkoutId)).andReturn().getResponse().getStatus();
            if (status != 200) {
                Thread.sleep(200);
            }
        }
        assertThat(status).as("investigation of %s", checkoutId).isEqualTo(200);
    }
}
