package com.smartcheckout.risk;

import com.smartcheckout.contracts.Topics;
import com.smartcheckout.contracts.checkout.CheckoutEvent;
import com.smartcheckout.contracts.checkout.CheckoutRequested;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The service with a real MongoDB and a real Kafka: CheckoutRequested in, RiskAssessed out.
 * Singleton containers: started once per test JVM, stopped after the Spring context at JVM exit.
 */
@SpringBootTest
class RiskServiceIntegrationTest {

    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        MONGO.start();
        KAFKA.start();
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void scores_a_checkout_and_publishes_an_explainable_decision() throws Exception {
        CheckoutRequested checkout = onlinePayment(899_900, "FR", "MA");

        send(checkout);

        RiskAssessed assessed = awaitRiskAssessed(checkout.checkoutId(), 1).getFirst();
        assertThat(assessed.decision()).isEqualTo("REVIEW");
        assertThat(assessed.score()).isEqualTo(60);
        assertThat(assessed.reasons()).extracting(RiskAssessed.Reason::code)
                .containsExactly("HIGH_AMOUNT", "GEO_MISMATCH");
    }

    @Test
    void a_replayed_event_gives_the_same_decision_with_the_same_event_id() throws Exception {
        CheckoutRequested checkout = onlinePayment(25_000, "MA", "MA");

        send(checkout);
        send(checkout);

        List<RiskAssessed> published = awaitRiskAssessed(checkout.checkoutId(), 2);
        assertThat(published).extracting(RiskAssessed::eventId).containsOnly(published.getFirst().eventId());
    }

    @Test
    void an_unreadable_message_goes_to_the_dead_letter_topic() throws Exception {
        String key = UUID.randomUUID().toString();

        kafkaTemplate.send(Topics.CHECKOUT_EVENTS, key, "{ this is not json").get();

        List<ConsumerRecord<String, String>> deadLetters = awaitRecords(Topics.CHECKOUT_EVENTS + ".DLT", key, 1);
        assertThat(deadLetters.getFirst().value()).isEqualTo("{ this is not json");
    }

    private static CheckoutRequested onlinePayment(long amountMinor, String cardCountry, String ipCountry) {
        return new CheckoutRequested(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "WEB-SHOP", "WEB",
                "C-" + UUID.randomUUID(), amountMinor, "MAD", "CARD", "tok_" + UUID.randomUUID(), "1881",
                cardCountry, ipCountry, "web-fp-1", 1);
    }

    private void send(CheckoutRequested checkout) throws Exception {
        String payload = jsonMapper.writerFor(CheckoutEvent.class).writeValueAsString(checkout);
        kafkaTemplate.send(Topics.CHECKOUT_EVENTS, checkout.checkoutId().toString(), payload).get();
    }

    private List<RiskAssessed> awaitRiskAssessed(UUID checkoutId, int expected) {
        return awaitRecords(Topics.RISK_EVENTS, checkoutId.toString(), expected).stream()
                .map(record -> jsonMapper.readValue(record.value(), RiskEvent.class))
                .map(RiskAssessed.class::cast)
                .toList();
    }

    private List<ConsumerRecord<String, String>> awaitRecords(String topic, String key, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline && matching.size() < expected) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        matching.add(record);
                    }
                }
            }
        }
        assertThat(matching).as("records with key %s on %s", key, topic).hasSize(expected);
        return matching;
    }
}
