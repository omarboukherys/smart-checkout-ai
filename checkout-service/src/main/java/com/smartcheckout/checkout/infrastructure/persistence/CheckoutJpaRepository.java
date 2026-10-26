package com.smartcheckout.checkout.infrastructure.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutJpaRepository extends JpaRepository<CheckoutEntity, UUID> {

    Optional<CheckoutEntity> findByIdempotencyKey(String idempotencyKey);

    List<CheckoutSummaryView> findAllByOrderByCreatedAtDesc(Limit limit);
}
