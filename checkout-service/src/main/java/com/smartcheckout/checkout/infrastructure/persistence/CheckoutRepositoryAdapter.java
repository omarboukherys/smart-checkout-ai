package com.smartcheckout.checkout.infrastructure.persistence;

import com.smartcheckout.checkout.application.CheckoutSummary;
import com.smartcheckout.checkout.application.port.CheckoutRepository;
import com.smartcheckout.checkout.domain.Checkout;
import com.smartcheckout.checkout.domain.CheckoutLine;
import com.smartcheckout.checkout.domain.ClientContext;
import com.smartcheckout.checkout.domain.PaymentDetails;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter between the application port and Spring Data JPA: maps domain objects to rows and back.
 */
@Component
class CheckoutRepositoryAdapter implements CheckoutRepository {

    private final CheckoutJpaRepository jpa;

    CheckoutRepositoryAdapter(CheckoutJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Checkout> findById(UUID id) {
        return jpa.findById(id).map(CheckoutRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<Checkout> findByIdempotencyKey(String idempotencyKey) {
        return jpa.findByIdempotencyKey(idempotencyKey).map(CheckoutRepositoryAdapter::toDomain);
    }

    @Override
    public void save(Checkout checkout) {
        CheckoutEntity entity = jpa.findById(checkout.id()).orElseGet(() -> toNewEntity(checkout));
        entity.applyState(checkout.status(), checkout.statusReason(), checkout.updatedAt());
        jpa.save(entity);
    }

    @Override
    public List<CheckoutSummary> findRecent(int limit) {
        return jpa.findAllByOrderByCreatedAtDesc(Limit.of(limit)).stream()
                .map(view -> new CheckoutSummary(view.getId(), view.getStoreId(), view.getChannel(),
                        view.getTotalMinor(), view.getCurrency(), view.getStatus(), view.getCreatedAt()))
                .toList();
    }

    static Checkout toDomain(CheckoutEntity entity) {
        List<CheckoutLine> lines = entity.lines().stream()
                .map(line -> new CheckoutLine(line.sku(), line.label(), line.quantity(), line.unitPriceMinor()))
                .toList();
        PaymentDetails payment = new PaymentDetails(entity.paymentMethod(), entity.paymentToken(),
                entity.cardLast4(), entity.cardCountry());
        return Checkout.rehydrate(entity.id(), entity.idempotencyKey(), entity.storeId(), entity.channel(),
                entity.customerId(), entity.currency(), lines, payment,
                new ClientContext(entity.ipCountry(), entity.deviceId()),
                entity.status(), entity.statusReason(), entity.createdAt(), entity.updatedAt());
    }

    static CheckoutEntity toNewEntity(Checkout checkout) {
        List<CheckoutLineEmbeddable> lines = checkout.lines().stream()
                .map(line -> new CheckoutLineEmbeddable(line.sku(), line.label(), line.quantity(), line.unitPriceMinor()))
                .toList();
        PaymentDetails payment = checkout.payment();
        return new CheckoutEntity(checkout.id(), checkout.idempotencyKey(), checkout.storeId(), checkout.channel(),
                checkout.customerId(), checkout.currency(), checkout.totalMinor(), payment.method(), payment.token(),
                payment.last4(), payment.country(), checkout.context().ipCountry(), checkout.context().deviceId(),
                checkout.createdAt(), lines);
    }
}
