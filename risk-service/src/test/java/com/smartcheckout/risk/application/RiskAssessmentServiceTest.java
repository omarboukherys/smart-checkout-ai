package com.smartcheckout.risk.application;

import com.smartcheckout.risk.application.port.AssessmentStore;
import com.smartcheckout.risk.application.port.RiskEventPublisher;
import com.smartcheckout.risk.domain.RiskDecision;
import com.smartcheckout.risk.domain.RiskEngine;
import com.smartcheckout.risk.domain.RiskReason;
import com.smartcheckout.risk.domain.TransactionFacts;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RiskAssessmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T14:00:00Z");

    private final InMemoryAssessmentStore store = new InMemoryAssessmentStore();
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final RiskAssessmentService service = new RiskAssessmentService(store, publisher,
            RiskEngine.withDefaultRules(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void scores_stores_and_publishes_a_new_checkout() {
        StoredAssessment stored = service.assess(UUID.randomUUID(),
                webPayment("chk-1", "C00077", 899_900, "tok_fr", "FR", "MA", "web-fp-1", NOW));

        assertThat(stored.assessment().decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(store.saved).hasSize(1);
        assertThat(publisher.published).containsExactly(stored.riskEventId());
    }

    @Test
    void a_replayed_event_republishes_the_stored_decision_with_the_same_event_id() {
        TransactionFacts payment = webPayment("chk-1", "C00077", 899_900, "tok_fr", "FR", "MA", "web-fp-1", NOW);

        StoredAssessment first = service.assess(UUID.randomUUID(), payment);
        StoredAssessment replay = service.assess(UUID.randomUUID(), payment);

        assertThat(store.saved).hasSize(1);
        assertThat(replay.riskEventId()).isEqualTo(first.riskEventId());
        assertThat(publisher.published).containsExactly(first.riskEventId(), first.riskEventId());
    }

    @Test
    void the_fifth_payment_with_the_same_card_in_ten_minutes_is_card_testing() {
        for (int i = 1; i <= 4; i++) {
            service.assess(UUID.randomUUID(), storePayment("chk-" + i, "tok_stolen", NOW.minusSeconds(60L * (5 - i))));
        }

        StoredAssessment fifth = service.assess(UUID.randomUUID(), storePayment("chk-5", "tok_stolen", NOW));

        assertThat(codes(fifth)).containsExactly("CARD_TESTING");
        assertThat(fifth.assessment().decision()).isEqualTo(RiskDecision.REVIEW);
    }

    @Test
    void payments_older_than_the_window_do_not_count() {
        for (int i = 1; i <= 4; i++) {
            service.assess(UUID.randomUUID(), storePayment("old-" + i, "tok_card", NOW.minusSeconds(3_600L + i)));
        }

        StoredAssessment current = service.assess(UUID.randomUUID(), storePayment("chk-now", "tok_card", NOW));

        assertThat(current.assessment().reasons()).isEmpty();
    }

    @Test
    void the_history_of_a_customer_is_returned_most_recent_first() {
        service.assess(UUID.randomUUID(), webPayment("chk-1", "C00042", 10_000, "tok_ma", "MA", "MA", "laptop", NOW.minusSeconds(86_400)));
        service.assess(UUID.randomUUID(), webPayment("chk-2", "C00042", 20_000, "tok_ma", "MA", "MA", "laptop", NOW));
        service.assess(UUID.randomUUID(), webPayment("chk-3", "C00099", 30_000, "tok_x", "MA", "MA", "phone", NOW));

        assertThat(service.history("C00042", 10)).extracting(s -> s.facts().checkoutId()).containsExactly("chk-2", "chk-1");
    }

    @Test
    void a_known_customer_on_a_new_device_is_flagged() {
        service.assess(UUID.randomUUID(), webPayment("chk-1", "C00042", 10_000, "tok_ma", "MA", "MA", "laptop", NOW.minusSeconds(86_400)));

        StoredAssessment fromNewPhone = service.assess(UUID.randomUUID(),
                webPayment("chk-2", "C00042", 10_000, "tok_ma", "MA", "MA", "new-phone", NOW));

        assertThat(codes(fromNewPhone)).containsExactly("NEW_DEVICE");
    }

    private static TransactionFacts storePayment(String checkoutId, String cardToken, Instant occurredAt) {
        return new TransactionFacts(checkoutId, "POS", null, 500, "MAD", cardToken, "MA", null, "POS-TERM-07", occurredAt);
    }

    private static TransactionFacts webPayment(String checkoutId, String customerId, long amountMinor, String cardToken,
                                               String cardCountry, String ipCountry, String deviceId, Instant occurredAt) {
        return new TransactionFacts(checkoutId, "WEB", customerId, amountMinor, "MAD", cardToken, cardCountry, ipCountry,
                deviceId, occurredAt);
    }

    private static List<String> codes(StoredAssessment stored) {
        return stored.assessment().reasons().stream().map(RiskReason::code).toList();
    }

    private static final class InMemoryAssessmentStore implements AssessmentStore {

        private final List<StoredAssessment> saved = new ArrayList<>();

        @Override
        public Optional<StoredAssessment> findByCheckoutId(String checkoutId) {
            return saved.stream().filter(s -> s.facts().checkoutId().equals(checkoutId)).findFirst();
        }

        @Override
        public void save(StoredAssessment assessment) {
            saved.add(assessment);
        }

        @Override
        public int countRecentPaymentsOnCard(String cardToken, Instant from, Instant to, String excludedCheckoutId) {
            return (int) saved.stream()
                    .map(StoredAssessment::facts)
                    .filter(f -> Objects.equals(f.cardToken(), cardToken))
                    .filter(f -> !f.checkoutId().equals(excludedCheckoutId))
                    .filter(f -> !f.occurredAt().isBefore(from) && !f.occurredAt().isAfter(to))
                    .count();
        }

        @Override
        public boolean customerHasHistory(String customerId, String excludedCheckoutId) {
            return saved.stream().map(StoredAssessment::facts)
                    .anyMatch(f -> customerId.equals(f.customerId()) && !f.checkoutId().equals(excludedCheckoutId));
        }

        @Override
        public List<StoredAssessment> findRecentByCustomer(String customerId, int limit) {
            return saved.stream()
                    .filter(s -> customerId.equals(s.facts().customerId()))
                    .sorted((a, b) -> b.facts().occurredAt().compareTo(a.facts().occurredAt()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean customerUsedDevice(String customerId, String deviceId, String excludedCheckoutId) {
            return saved.stream().map(StoredAssessment::facts)
                    .anyMatch(f -> customerId.equals(f.customerId()) && deviceId.equals(f.deviceId())
                            && !f.checkoutId().equals(excludedCheckoutId));
        }
    }

    private static final class RecordingPublisher implements RiskEventPublisher {

        private final List<UUID> published = new ArrayList<>();

        @Override
        public void publish(StoredAssessment assessment) {
            published.add(assessment.riskEventId());
        }
    }
}
