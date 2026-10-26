package com.smartcheckout.risk.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * One assessment in MongoDB. The checkout id is the document id: a second insert for the same
 * checkout fails, which makes the store idempotent by construction.
 * <p>
 * The card token is never stored, only its SHA-256 fingerprint: enough to count payments per card.
 */
@Document(collection = "risk_assessments")
@CompoundIndexes({
        @CompoundIndex(name = "card_time", def = "{'cardFingerprint': 1, 'occurredAt': -1}"),
        @CompoundIndex(name = "customer_device", def = "{'customerId': 1, 'deviceId': 1}")
})
public class RiskAssessmentDocument {

    @Id
    private String checkoutId;
    private String riskEventId;
    private String sourceEventId;
    private String channel;
    private String customerId;
    private long amountMinor;
    private String currency;
    private String cardFingerprint;
    private String cardCountry;
    private String ipCountry;
    private String deviceId;
    private Instant occurredAt;
    private int score;
    private String decision;
    private List<Reason> reasons;
    private Instant assessedAt;

    public record Reason(String code, int points, String detail) {
    }

    protected RiskAssessmentDocument() {
        // required by Spring Data
    }

    @SuppressWarnings("java:S107") // a flat document is clearer than nested builders here
    RiskAssessmentDocument(String checkoutId, String riskEventId, String sourceEventId, String channel,
                           String customerId, long amountMinor, String currency, String cardFingerprint,
                           String cardCountry, String ipCountry, String deviceId, Instant occurredAt,
                           int score, String decision, List<Reason> reasons, Instant assessedAt) {
        this.checkoutId = checkoutId;
        this.riskEventId = riskEventId;
        this.sourceEventId = sourceEventId;
        this.channel = channel;
        this.customerId = customerId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.cardFingerprint = cardFingerprint;
        this.cardCountry = cardCountry;
        this.ipCountry = ipCountry;
        this.deviceId = deviceId;
        this.occurredAt = occurredAt;
        this.score = score;
        this.decision = decision;
        this.reasons = reasons;
        this.assessedAt = assessedAt;
    }

    String checkoutId() {
        return checkoutId;
    }

    String riskEventId() {
        return riskEventId;
    }

    String sourceEventId() {
        return sourceEventId;
    }

    String channel() {
        return channel;
    }

    String customerId() {
        return customerId;
    }

    long amountMinor() {
        return amountMinor;
    }

    String currency() {
        return currency;
    }

    String cardCountry() {
        return cardCountry;
    }

    String ipCountry() {
        return ipCountry;
    }

    String deviceId() {
        return deviceId;
    }

    Instant occurredAt() {
        return occurredAt;
    }

    int score() {
        return score;
    }

    String decision() {
        return decision;
    }

    List<Reason> reasons() {
        return reasons;
    }

    Instant assessedAt() {
        return assessedAt;
    }
}
