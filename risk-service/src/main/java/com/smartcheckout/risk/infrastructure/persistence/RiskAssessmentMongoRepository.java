package com.smartcheckout.risk.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

public interface RiskAssessmentMongoRepository extends MongoRepository<RiskAssessmentDocument, String> {

    long countByCardFingerprintAndOccurredAtBetweenAndCheckoutIdNot(String cardFingerprint, Instant from,
                                                                   Instant to, String checkoutId);

    boolean existsByCustomerIdAndCheckoutIdNot(String customerId, String checkoutId);

    boolean existsByCustomerIdAndDeviceIdAndCheckoutIdNot(String customerId, String deviceId, String checkoutId);
}
