package com.smartcheckout.checkout.application;

import com.smartcheckout.checkout.application.port.CheckoutEventPublisher;
import com.smartcheckout.checkout.application.port.CheckoutRepository;
import com.smartcheckout.checkout.domain.Channel;
import com.smartcheckout.checkout.domain.Checkout;
import com.smartcheckout.checkout.domain.CheckoutLine;
import com.smartcheckout.checkout.domain.ClientContext;
import com.smartcheckout.checkout.domain.PaymentDetails;
import com.smartcheckout.checkout.domain.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the use cases with in-memory fakes of the ports: this is what the ports make possible.
 */
class CheckoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private final InMemoryCheckoutRepository repository = new InMemoryCheckoutRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();
    private final CheckoutService service = new CheckoutService(repository, events, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void opens_a_checkout_stores_it_and_announces_it() {
        OpenCheckoutResult result = service.open(command("key-1"));

        assertThat(result.created()).isTrue();
        assertThat(result.checkout().createdAt()).isEqualTo(NOW);
        assertThat(repository.findById(result.checkout().id())).isPresent();
        assertThat(events.published).containsExactly(result.checkout().id());
    }

    @Test
    void replaying_an_idempotency_key_returns_the_first_checkout_without_a_new_event() {
        OpenCheckoutResult first = service.open(command("key-1"));

        OpenCheckoutResult replay = service.open(command("key-1"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.checkout().id()).isEqualTo(first.checkout().id());
        assertThat(events.published).hasSize(1);
    }

    @Test
    void different_keys_create_different_checkouts() {
        service.open(command("key-1"));
        service.open(command("key-2"));

        assertThat(service.recent(10)).hasSize(2);
        assertThat(events.published).hasSize(2);
    }

    @Test
    void reports_an_unknown_checkout() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(CheckoutNotFoundException.class);
    }

    private static OpenCheckoutCommand command(String idempotencyKey) {
        return new OpenCheckoutCommand(idempotencyKey, "CASA-01", Channel.POS, null, "MAD",
                List.of(new CheckoutLine("SKU-CAFE-500", "Cafe moulu 500 g", 2, 4_590)),
                new PaymentDetails(PaymentMethod.CARD, "tok_visa_4242", "4242", "MA"),
                ClientContext.none());
    }

    private static final class InMemoryCheckoutRepository implements CheckoutRepository {

        private final Map<UUID, Checkout> store = new LinkedHashMap<>();

        @Override
        public Optional<Checkout> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Checkout> findByIdempotencyKey(String idempotencyKey) {
            return store.values().stream()
                    .filter(checkout -> checkout.idempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public void save(Checkout checkout) {
            store.put(checkout.id(), checkout);
        }

        @Override
        public List<CheckoutSummary> findRecent(int limit) {
            return store.values().stream()
                    .map(c -> new CheckoutSummary(c.id(), c.storeId(), c.channel(), c.totalMinor(), c.currency(),
                            c.status(), c.createdAt()))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class RecordingEventPublisher implements CheckoutEventPublisher {

        private final List<UUID> published = new ArrayList<>();

        @Override
        public void checkoutRequested(Checkout checkout) {
            published.add(checkout.id());
        }
    }
}
