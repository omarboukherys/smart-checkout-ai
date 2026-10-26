package com.smartcheckout.payment;

import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.payment.AuthorizePayment;
import com.smartcheckout.contracts.payment.PaymentAuthorized;
import com.smartcheckout.contracts.payment.PaymentCommand;
import com.smartcheckout.contracts.payment.PaymentDeclined;
import com.smartcheckout.contracts.payment.PaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * payment-service with a real PostgreSQL and a real Kafka: AuthorizePayment in, payment outcome out.
 * <p>
 * Singleton containers: started once for the whole test JVM and stopped by Testcontainers when the JVM
 * exits, after the Spring context has shut down. No reconnection attempts to a stopped broker.
 */
@SpringBootTest(properties = "smartcheckout.psp.latency-ms=0")
class PaymentServiceIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void authorizes_a_payment_and_publishes_the_outcome() throws Exception {
        UUID checkoutId = UUID.randomUUID();

        send(authorize(checkoutId, "tok_visa_4242"));

        PaymentEvent outcome = awaitOutcomes(checkoutId, 1).getFirst();
        assertThat(outcome).isInstanceOfSatisfying(PaymentAuthorized.class, authorized -> {
            assertThat(authorized.amountMinor()).isEqualTo(11_430L);
            assertThat(authorized.pspReference()).startsWith("psp_");
        });
    }

    @Test
    void a_replayed_command_republishes_the_same_outcome_without_a_second_charge() throws Exception {
        UUID checkoutId = UUID.randomUUID();
        AuthorizePayment command = authorize(checkoutId, "tok_visa_4242");

        send(command);
        send(command);

        List<PaymentEvent> outcomes = awaitOutcomes(checkoutId, 2);
        assertThat(outcomes).extracting(PaymentEvent::eventId).containsOnly(outcomes.getFirst().eventId());
        assertThat(outcomes).extracting(event -> ((PaymentAuthorized) event).pspReference())
                .containsOnly(((PaymentAuthorized) outcomes.getFirst()).pspReference());
    }

    @Test
    void a_card_without_funds_is_declined() throws Exception {
        UUID checkoutId = UUID.randomUUID();

        send(authorize(checkoutId, "tok_insufficient_01"));

        assertThat(awaitOutcomes(checkoutId, 1).getFirst()).isInstanceOfSatisfying(PaymentDeclined.class,
                declined -> assertThat(declined.reasonCode()).isEqualTo("INSUFFICIENT_FUNDS"));
    }

    @Test
    void a_flaky_provider_is_retried_until_it_answers() throws Exception {
        UUID checkoutId = UUID.randomUUID();

        send(authorize(checkoutId, "tok_flaky_01"));

        assertThat(awaitOutcomes(checkoutId, 1).getFirst()).isInstanceOf(PaymentAuthorized.class);
    }

    private static AuthorizePayment authorize(UUID checkoutId, String token) {
        return new AuthorizePayment(UUID.randomUUID(), checkoutId, Instant.now(), 11_430L, "MAD", "CARD", token);
    }

    private void send(AuthorizePayment command) throws Exception {
        String payload = jsonMapper.writerFor(PaymentCommand.class).writeValueAsString(command);
        kafkaTemplate.send(Topics.PAYMENT_COMMANDS, command.checkoutId().toString(), payload).get();
    }

    private List<PaymentEvent> awaitOutcomes(UUID checkoutId, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<PaymentEvent> outcomes = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(Topics.PAYMENT_EVENTS));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline && outcomes.size() < expected) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (checkoutId.toString().equals(record.key())) {
                        outcomes.add(jsonMapper.readValue(record.value(), PaymentEvent.class));
                    }
                }
            }
        }
        assertThat(outcomes).as("payment outcomes for %s", checkoutId).hasSize(expected);
        return outcomes;
    }
}
