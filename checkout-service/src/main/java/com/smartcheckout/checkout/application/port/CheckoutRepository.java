package com.smartcheckout.checkout.application.port;

import com.smartcheckout.checkout.application.CheckoutSummary;
import com.smartcheckout.checkout.domain.Checkout;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port: how the application stores checkouts. Implemented by the JPA adapter.
 */
public interface CheckoutRepository {

    Optional<Checkout> findById(UUID id);

    Optional<Checkout> findByIdempotencyKey(String idempotencyKey);

    void save(Checkout checkout);

    List<CheckoutSummary> findRecent(int limit);
}
