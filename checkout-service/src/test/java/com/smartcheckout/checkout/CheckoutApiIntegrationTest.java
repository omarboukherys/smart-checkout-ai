package com.smartcheckout.checkout;

import com.jayway.jsonpath.JsonPath;
import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.checkout.CheckoutEvent;
import com.smartcheckout.contracts.checkout.CheckoutRequested;
import com.smartcheckout.contracts.payment.AuthorizePayment;
import com.smartcheckout.contracts.payment.PaymentAuthorized;
import com.smartcheckout.contracts.payment.PaymentCommand;
import com.smartcheckout.contracts.payment.PaymentEvent;
import com.smartcheckout.contracts.risk.RiskAssessed;
import com.smartcheckout.contracts.risk.RiskEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * checkout-service with a real PostgreSQL and a real Kafka. The other services are played by the test:
 * it publishes their events and reads the commands sent to them.
 * <p>
 * Singleton containers: started once per test JVM, stopped after the Spring context at JVM exit.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutApiIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    private static final String POS_CHECKOUT = """
            {
              "storeId": "CASA-MAARIF-01",
              "channel": "POS",
              "currency": "MAD",
              "lines": [
                { "sku": "SKU-CAFE-500", "label": "Cafe moulu 500 g", "quantity": 2, "unitPriceMinor": 4590 },
                { "sku": "SKU-THE-250", "label": "The vert 250 g", "quantity": 1, "unitPriceMinor": 2250 }
              ],
              "payment": { "method": "CARD", "token": "%s", "last4": "4242", "country": "MA" },
              "context": { "ipCountry": "MA", "deviceId": "POS-TERM-07" }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void opens_a_checkout_idempotently_and_publishes_it_through_the_outbox() throws Exception {
        String key = UUID.randomUUID().toString();

        MvcResult created = mockMvc.perform(post("/api/v1/checkouts")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POS_CHECKOUT.formatted("tok_visa_4242")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalMinor").value(11430))
                .andExpect(jsonPath("$.total").value("114.30"))
                .andExpect(jsonPath("$.paymentToken").doesNotExist())
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/checkouts")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POS_CHECKOUT.formatted("tok_visa_4242")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        CheckoutEvent event = jsonMapper.readValue(awaitRecord(Topics.CHECKOUT_EVENTS, id), CheckoutEvent.class);
        assertThat(event).isInstanceOfSatisfying(CheckoutRequested.class, requested -> {
            assertThat(requested.amountMinor()).isEqualTo(11_430L);
            assertThat(requested.cardToken()).isEqualTo("tok_visa_4242");
            assertThat(requested.itemCount()).isEqualTo(3);
        });
    }

    @Test
    void an_approved_checkout_is_sent_to_payment_then_marked_paid() throws Exception {
        String id = openCheckout();

        publishRiskDecision(id, "APPROVE", 0);

        PaymentCommand command = jsonMapper.readValue(awaitRecord(Topics.PAYMENT_COMMANDS, id), PaymentCommand.class);
        assertThat(command).isInstanceOfSatisfying(AuthorizePayment.class, authorize -> {
            assertThat(authorize.amountMinor()).isEqualTo(11_430L);
            assertThat(authorize.paymentToken()).isEqualTo("tok_visa_4242");
        });

        publishPaymentAuthorized(id);
        awaitStatus(id, "PAID");
    }

    @Test
    void a_held_checkout_is_paid_after_a_supervisor_approval() throws Exception {
        String id = openCheckout();

        publishRiskDecision(id, "REVIEW", 60);
        awaitStatus(id, "ON_HOLD");

        mockMvc.perform(post("/api/v1/checkouts/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decision": "APPROVE", "reviewer": "amina.supervisor", "comment": "Confirmed by phone" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusReason").value("Approved by amina.supervisor, payment in progress"));

        awaitRecord(Topics.PAYMENT_COMMANDS, id);
        publishPaymentAuthorized(id);
        awaitStatus(id, "PAID");
    }

    @Test
    void a_checkout_that_is_not_on_hold_cannot_be_reviewed() throws Exception {
        String id = openCheckout();

        mockMvc.perform(post("/api/v1/checkouts/{id}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decision": "APPROVE", "reviewer": "amina.supervisor" }
                                """))
                .andExpect(status().is(422));
    }

    @Test
    void refuses_a_raw_card_number_with_422() throws Exception {
        mockMvc.perform(post("/api/v1/checkouts")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POS_CHECKOUT.formatted("4111 1111 1111 1111")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Business rule violated"));
    }

    @Test
    void rejects_a_request_without_idempotency_key_with_400() throws Exception {
        mockMvc.perform(post("/api/v1/checkouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POS_CHECKOUT.formatted("tok_visa_4242")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns_404_for_an_unknown_checkout() throws Exception {
        mockMvc.perform(get("/api/v1/checkouts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private String openCheckout() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/checkouts")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POS_CHECKOUT.formatted("tok_visa_4242")))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private void publishRiskDecision(String checkoutId, String decision, int score) throws Exception {
        RiskEvent event = new RiskAssessed(UUID.randomUUID(), UUID.fromString(checkoutId), Instant.now(), decision,
                score, List.of());
        kafkaTemplate.send(Topics.RISK_EVENTS, checkoutId,
                jsonMapper.writerFor(RiskEvent.class).writeValueAsString(event)).get();
    }

    private void publishPaymentAuthorized(String checkoutId) throws Exception {
        PaymentEvent event = new PaymentAuthorized(UUID.randomUUID(), UUID.fromString(checkoutId), Instant.now(),
                "psp_it_001", 11_430L, "MAD");
        kafkaTemplate.send(Topics.PAYMENT_EVENTS, checkoutId,
                jsonMapper.writerFor(PaymentEvent.class).writeValueAsString(event)).get();
    }

    private void awaitStatus(String checkoutId, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        String current = null;
        while (System.nanoTime() < deadline) {
            String body = mockMvc.perform(get("/api/v1/checkouts/{id}", checkoutId))
                    .andReturn().getResponse().getContentAsString();
            current = JsonPath.read(body, "$.status");
            if (expected.equals(current)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Checkout " + checkoutId + " is " + current + ", expected " + expected);
    }

    private String awaitRecord(String topic, String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record.value();
                    }
                }
            }
        }
        throw new AssertionError("No record with key " + key + " on " + topic);
    }
}
