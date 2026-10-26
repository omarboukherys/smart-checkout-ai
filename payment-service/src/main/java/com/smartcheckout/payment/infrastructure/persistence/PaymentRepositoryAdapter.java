package com.smartcheckout.payment.infrastructure.persistence;

import com.smartcheckout.payment.application.port.PaymentRepository;
import com.smartcheckout.payment.domain.Payment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Each call is its own short transaction (Spring Data repositories are transactional):
 * the AUTHORIZING row is committed before the provider is called.
 */
@Component
class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpa;

    PaymentRepositoryAdapter(PaymentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Payment> findByCheckoutId(String checkoutId) {
        return jpa.findById(checkoutId).map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    public void insert(Payment payment) {
        PaymentEntity entity = new PaymentEntity(payment.checkoutId(), payment.amountMinor(), payment.currency(),
                payment.paymentMethod(), payment.createdAt());
        copyState(payment, entity);
        jpa.saveAndFlush(entity);
    }

    @Override
    public void update(Payment payment) {
        PaymentEntity entity = jpa.findById(payment.checkoutId())
                .orElseThrow(() -> new IllegalStateException("Unknown payment " + payment.checkoutId()));
        copyState(payment, entity);
        jpa.save(entity);
    }

    private static void copyState(Payment payment, PaymentEntity entity) {
        entity.applyState(payment.status(), payment.pspReference(), payment.declineCode(), payment.declineMessage(),
                payment.updatedAt());
    }

    private static Payment toDomain(PaymentEntity entity) {
        return Payment.rehydrate(entity.checkoutId(), entity.amountMinor(), entity.currency(), entity.paymentMethod(),
                entity.status(), entity.pspReference(), entity.declineCode(), entity.declineMessage(),
                entity.createdAt(), entity.updatedAt());
    }
}
