package com.smartcheckout.checkout.application;

import com.smartcheckout.checkout.application.port.PaymentCommandPublisher;
import com.smartcheckout.checkout.application.port.ProcessedEvents;
import com.smartcheckout.checkout.domain.Channel;
import com.smartcheckout.checkout.domain.Checkout;
import com.smartcheckout.checkout.domain.CheckoutLine;
import com.smartcheckout.checkout.domain.CheckoutStatus;
import com.smartcheckout.checkout.domain.ClientContext;
import com.smartcheckout.checkout.domain.DomainException;
import com.smartcheckout.checkout.domain.PaymentDetails;
import com.smartcheckout.checkout.domain.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutOrchestratorTest {

    private static final Instant NOW = Instant.parse("2026-10-26T16:00:00Z");

    private final InMemoryCheckoutRepository repository = new InMemoryCheckoutRepository();
    private final InMemoryInbox inbox = new InMemoryInbox();
    private final RecordingPaymentCommands payments = new RecordingPaymentCommands();
    private final CheckoutOrchestrator orchestrator = new CheckoutOrchestrator(repository, inbox, payments,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void an_approved_checkout_is_sent_to_payment_and_stays_pending() {
        Checkout checkout = pendingCheckout();

        orchestrator.onRiskAssessed(UUID.randomUUID(), checkout.id(), "APPROVE", 0, List.of());

        assertThat(payments.requested).containsExactly(checkout.id());
        assertThat(status(checkout)).isEqualTo(CheckoutStatus.PENDING_PAYMENT);
    }

    @Test
    void a_checkout_to_review_is_put_on_hold_with_the_reasons() {
        Checkout checkout = pendingCheckout();

        orchestrator.onRiskAssessed(UUID.randomUUID(), checkout.id(), "REVIEW", 60,
                List.of("HIGH_AMOUNT", "GEO_MISMATCH"));

        Checkout held = repository.findById(checkout.id()).orElseThrow();
        assertThat(held.status()).isEqualTo(CheckoutStatus.ON_HOLD);
        assertThat(held.statusReason()).contains("score 60", "HIGH_AMOUNT", "GEO_MISMATCH");
        assertThat(payments.requested).isEmpty();
    }

    @Test
    void a_blocked_checkout_is_rejected() {
        Checkout checkout = pendingCheckout();

        orchestrator.onRiskAssessed(UUID.randomUUID(), checkout.id(), "BLOCK", 80, List.of("VERY_HIGH_AMOUNT"));

        assertThat(status(checkout)).isEqualTo(CheckoutStatus.REJECTED);
        assertThat(payments.requested).isEmpty();
    }

    @Test
    void a_duplicate_risk_event_is_applied_only_once() {
        Checkout checkout = pendingCheckout();
        UUID eventId = UUID.randomUUID();

        orchestrator.onRiskAssessed(eventId, checkout.id(), "APPROVE", 0, List.of());
        orchestrator.onRiskAssessed(eventId, checkout.id(), "APPROVE", 0, List.of());

        assertThat(payments.requested).hasSize(1);
    }

    @Test
    void an_authorized_payment_marks_the_checkout_paid() {
        Checkout checkout = pendingCheckout();

        orchestrator.onPaymentAuthorized(UUID.randomUUID(), checkout.id(), "psp_001");

        Checkout paid = repository.findById(checkout.id()).orElseThrow();
        assertThat(paid.status()).isEqualTo(CheckoutStatus.PAID);
        assertThat(paid.statusReason()).contains("psp_001");
    }

    @Test
    void a_declined_payment_keeps_the_provider_reason() {
        Checkout checkout = pendingCheckout();

        orchestrator.onPaymentDeclined(UUID.randomUUID(), checkout.id(), "INSUFFICIENT_FUNDS", "Insufficient funds");

        Checkout declined = repository.findById(checkout.id()).orElseThrow();
        assertThat(declined.status()).isEqualTo(CheckoutStatus.DECLINED);
        assertThat(declined.statusReason()).startsWith("INSUFFICIENT_FUNDS");
    }

    @Test
    void a_reviewer_approval_sends_the_held_checkout_to_payment_which_completes_it() {
        Checkout checkout = heldCheckout();

        orchestrator.review(checkout.id(), ReviewDecision.APPROVE, "amina.supervisor", "Customer called the store");
        orchestrator.onPaymentAuthorized(UUID.randomUUID(), checkout.id(), "psp_002");

        assertThat(payments.requested).containsExactly(checkout.id());
        assertThat(status(checkout)).isEqualTo(CheckoutStatus.PAID);
    }

    @Test
    void a_reviewer_can_reject_a_held_checkout() {
        Checkout checkout = heldCheckout();

        Checkout rejected = orchestrator.review(checkout.id(), ReviewDecision.REJECT, "amina.supervisor",
                "Card country does not match the customer");

        assertThat(rejected.status()).isEqualTo(CheckoutStatus.REJECTED);
        assertThat(rejected.statusReason()).contains("amina.supervisor", "does not match");
        assertThat(payments.requested).isEmpty();
    }

    @Test
    void only_a_checkout_on_hold_can_be_reviewed() {
        Checkout checkout = pendingCheckout();

        assertThatThrownBy(() -> orchestrator.review(checkout.id(), ReviewDecision.APPROVE, "amina.supervisor", null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("on hold");
    }

    @Test
    void a_late_payment_event_on_a_finished_checkout_is_ignored() {
        Checkout checkout = pendingCheckout();
        orchestrator.onRiskAssessed(UUID.randomUUID(), checkout.id(), "BLOCK", 80, List.of("VERY_HIGH_AMOUNT"));

        orchestrator.onPaymentAuthorized(UUID.randomUUID(), checkout.id(), "psp_late");

        assertThat(status(checkout)).isEqualTo(CheckoutStatus.REJECTED);
    }

    private Checkout pendingCheckout() {
        Checkout checkout = Checkout.open("key-" + UUID.randomUUID(), "WEB-SHOP", Channel.WEB, "C00077", "MAD",
                List.of(new CheckoutLine("SKU-LAPTOP-14", "Laptop", 1, 899_900)),
                new PaymentDetails(PaymentMethod.CARD, "tok_visa_fr_1881", "1881", "FR"),
                new ClientContext("MA", "web-fp-1"), NOW.minusSeconds(5));
        repository.save(checkout);
        return checkout;
    }

    private Checkout heldCheckout() {
        Checkout checkout = pendingCheckout();
        orchestrator.onRiskAssessed(UUID.randomUUID(), checkout.id(), "REVIEW", 60, List.of("HIGH_AMOUNT"));
        return checkout;
    }

    private CheckoutStatus status(Checkout checkout) {
        return repository.findById(checkout.id()).orElseThrow().status();
    }

    private static final class InMemoryInbox implements ProcessedEvents {

        private final Set<UUID> seen = new HashSet<>();

        @Override
        public boolean markProcessed(UUID eventId) {
            return seen.add(eventId);
        }
    }

    private static final class RecordingPaymentCommands implements PaymentCommandPublisher {

        private final List<UUID> requested = new ArrayList<>();

        @Override
        public void requestAuthorization(Checkout checkout) {
            requested.add(checkout.id());
        }
    }
}
