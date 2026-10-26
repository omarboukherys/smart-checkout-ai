package com.smartcheckout.checkout.application;

import com.smartcheckout.checkout.application.port.CheckoutRepository;
import com.smartcheckout.checkout.domain.Checkout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory fake of the repository port, shared by the use case tests.
 */
final class InMemoryCheckoutRepository implements CheckoutRepository {

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
