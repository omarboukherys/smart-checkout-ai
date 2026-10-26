package com.smartcheckout.payment.application;

import com.smartcheckout.payment.application.port.AuthorizationRequest;
import com.smartcheckout.payment.application.port.PaymentEventPublisher;
import com.smartcheckout.payment.application.port.PaymentProvider;
import com.smartcheckout.payment.application.port.PaymentProviderUnavailableException;
import com.smartcheckout.payment.application.port.PaymentRepository;
import com.smartcheckout.payment.domain.AuthorizationResult;
import com.smartcheckout.payment.domain.Payment;
import com.smartcheckout.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");

    private final InMemoryPaymentRepository repository = new InMemoryPaymentRepository();
    private final ScriptedProvider provider = new ScriptedProvider();
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final PaymentService service = new PaymentService(repository, provider, publisher,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void authorizes_with_the_checkout_id_as_idempotency_key_and_publishes_the_result() {
        provider.answer = new AuthorizationResult.Approved("psp_001");

        Payment payment = service.authorize(command("chk-1"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(provider.requests).extracting(AuthorizationRequest::idempotencyKey).containsExactly("chk-1");
        assertThat(repository.find("chk-1").status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(publisher.published).containsExactly("chk-1:AUTHORIZED");
    }

    @Test
    void records_a_decline_as_a_final_answer() {
        provider.answer = new AuthorizationResult.Declined("INSUFFICIENT_FUNDS", "Insufficient funds");

        Payment payment = service.authorize(command("chk-1"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(publisher.published).containsExactly("chk-1:DECLINED");
    }

    @Test
    void a_replayed_command_does_not_call_the_provider_again() {
        provider.answer = new AuthorizationResult.Approved("psp_001");
        service.authorize(command("chk-1"));

        service.authorize(command("chk-1"));

        assertThat(provider.requests).hasSize(1);
        assertThat(publisher.published).containsExactly("chk-1:AUTHORIZED", "chk-1:AUTHORIZED");
    }

    @Test
    void after_a_crash_the_provider_is_called_again_with_the_same_key() {
        repository.insert(Payment.start("chk-1", 11_430, "MAD", "CARD", NOW.minusSeconds(30)));
        provider.answer = new AuthorizationResult.Approved("psp_001");

        Payment payment = service.authorize(command("chk-1"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(provider.requests).extracting(AuthorizationRequest::idempotencyKey).containsExactly("chk-1");
    }

    @Test
    void a_provider_outage_leaves_the_payment_authorizing_and_publishes_nothing() {
        provider.unavailable = true;

        assertThatThrownBy(() -> service.authorize(command("chk-1")))
                .isInstanceOf(PaymentProviderUnavailableException.class);

        assertThat(repository.find("chk-1").status()).isEqualTo(PaymentStatus.AUTHORIZING);
        assertThat(publisher.published).isEmpty();
    }

    private static AuthorizePaymentCommand command(String checkoutId) {
        return new AuthorizePaymentCommand(checkoutId, 11_430, "MAD", "CARD", "tok_visa_4242");
    }

    private static final class InMemoryPaymentRepository implements PaymentRepository {

        private final Map<String, Payment> payments = new HashMap<>();

        Payment find(String checkoutId) {
            return payments.get(checkoutId);
        }

        @Override
        public Optional<Payment> findByCheckoutId(String checkoutId) {
            return Optional.ofNullable(payments.get(checkoutId));
        }

        @Override
        public void insert(Payment payment) {
            if (payments.putIfAbsent(payment.checkoutId(), payment) != null) {
                throw new IllegalStateException("duplicate payment " + payment.checkoutId());
            }
        }

        @Override
        public void update(Payment payment) {
            payments.put(payment.checkoutId(), payment);
        }
    }

    private static final class ScriptedProvider implements PaymentProvider {

        private final List<AuthorizationRequest> requests = new ArrayList<>();
        private AuthorizationResult answer;
        private boolean unavailable;

        @Override
        public AuthorizationResult authorize(AuthorizationRequest request) {
            requests.add(request);
            if (unavailable) {
                throw new PaymentProviderUnavailableException("PSP timeout");
            }
            return answer;
        }
    }

    private static final class RecordingPublisher implements PaymentEventPublisher {

        private final List<String> published = new ArrayList<>();

        @Override
        public void publish(Payment payment) {
            published.add(payment.checkoutId() + ":" + payment.status());
        }
    }
}
