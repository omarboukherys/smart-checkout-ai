package com.smartcheckout.risk.infrastructure.persistence;

import com.smartcheckout.risk.application.StoredAssessment;
import com.smartcheckout.risk.application.port.AssessmentStore;
import com.smartcheckout.risk.domain.RiskAssessment;
import com.smartcheckout.risk.domain.RiskDecision;
import com.smartcheckout.risk.domain.RiskReason;
import com.smartcheckout.risk.domain.TransactionFacts;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class MongoAssessmentStore implements AssessmentStore {

    private final RiskAssessmentMongoRepository repository;

    MongoAssessmentStore(RiskAssessmentMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StoredAssessment> findByCheckoutId(String checkoutId) {
        return repository.findById(checkoutId).map(MongoAssessmentStore::toStored);
    }

    /** insert, not save: an existing checkout is never silently overwritten. */
    @Override
    public void save(StoredAssessment assessment) {
        repository.insert(toDocument(assessment));
    }

    @Override
    public int countRecentPaymentsOnCard(String cardToken, Instant from, Instant to, String excludedCheckoutId) {
        return Math.toIntExact(repository.countByCardFingerprintAndOccurredAtBetweenAndCheckoutIdNot(
                CardFingerprints.of(cardToken), from, to, excludedCheckoutId));
    }

    @Override
    public boolean customerHasHistory(String customerId, String excludedCheckoutId) {
        return repository.existsByCustomerIdAndCheckoutIdNot(customerId, excludedCheckoutId);
    }

    @Override
    public boolean customerUsedDevice(String customerId, String deviceId, String excludedCheckoutId) {
        return repository.existsByCustomerIdAndDeviceIdAndCheckoutIdNot(customerId, deviceId, excludedCheckoutId);
    }

    @Override
    public List<StoredAssessment> findRecentByCustomer(String customerId, int limit) {
        return repository.findByCustomerIdOrderByOccurredAtDesc(customerId, Limit.of(limit)).stream()
                .map(MongoAssessmentStore::toStored)
                .toList();
    }

    private static RiskAssessmentDocument toDocument(StoredAssessment stored) {
        TransactionFacts facts = stored.facts();
        RiskAssessment assessment = stored.assessment();
        List<RiskAssessmentDocument.Reason> reasons = assessment.reasons().stream()
                .map(reason -> new RiskAssessmentDocument.Reason(reason.code(), reason.points(), reason.detail()))
                .toList();
        return new RiskAssessmentDocument(facts.checkoutId(), stored.riskEventId().toString(),
                stored.sourceEventId().toString(), facts.channel(), facts.customerId(), facts.amountMinor(),
                facts.currency(), CardFingerprints.of(facts.cardToken()), facts.cardCountry(), facts.ipCountry(),
                facts.deviceId(), facts.occurredAt(), assessment.score(), assessment.decision().name(), reasons,
                assessment.assessedAt());
    }

    /** The card token is not stored, so it comes back as null. */
    private static StoredAssessment toStored(RiskAssessmentDocument document) {
        TransactionFacts facts = new TransactionFacts(document.checkoutId(), document.channel(),
                document.customerId(), document.amountMinor(), document.currency(), null,
                document.cardCountry(), document.ipCountry(), document.deviceId(), document.occurredAt());
        List<RiskReason> reasons = document.reasons().stream()
                .map(reason -> new RiskReason(reason.code(), reason.points(), reason.detail()))
                .toList();
        RiskAssessment assessment = new RiskAssessment(document.checkoutId(), document.score(),
                RiskDecision.valueOf(document.decision()), reasons, document.assessedAt());
        return new StoredAssessment(UUID.fromString(document.riskEventId()), UUID.fromString(document.sourceEventId()),
                facts, assessment);
    }
}
