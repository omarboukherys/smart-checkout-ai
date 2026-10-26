package com.smartcheckout.checkout;

import com.jayway.jsonpath.JsonPath;
import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.checkout.CheckoutEvent;
import com.smartcheckout.contracts.checkout.CheckoutRequested;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
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
 * End-to-end test of the service with a real PostgreSQL and a real Kafka started by Testcontainers:
 * HTTP request → database → outbox → Kafka topic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckoutApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.0");

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

        CheckoutRequested event = awaitCheckoutRequested(id);
        assertThat(event.amountMinor()).isEqualTo(11_430L);
        assertThat(event.cardToken()).isEqualTo("tok_visa_4242");
        assertThat(event.itemCount()).isEqualTo(3);
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

    private CheckoutRequested awaitCheckoutRequested(String checkoutId) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(Topics.CHECKOUT_EVENTS));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (checkoutId.equals(record.key())
                            && jsonMapper.readValue(record.value(), CheckoutEvent.class) instanceof CheckoutRequested requested) {
                        return requested;
                    }
                }
            }
        }
        throw new AssertionError("No CheckoutRequested event received for checkout " + checkoutId);
    }
}
