package com.smartcheckout.checkout.infrastructure.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One event waiting to be published to Kafka (transactional outbox pattern).
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected OutboxEventEntity() {
        // required by JPA
    }

    static OutboxEventEntity pending(UUID eventId, String aggregateId, String topic, String eventType,
                                     String payload, Instant createdAt) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = eventId;
        entity.aggregateId = aggregateId;
        entity.topic = topic;
        entity.eventType = eventType;
        entity.payload = payload;
        entity.createdAt = createdAt;
        return entity;
    }

    void markPublished(Instant at) {
        this.publishedAt = at;
        this.attempts++;
        this.lastError = null;
    }

    void markFailed(String error) {
        this.attempts++;
        String message = error == null ? "unknown error" : error;
        this.lastError = message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    UUID id() {
        return id;
    }

    String aggregateId() {
        return aggregateId;
    }

    String topic() {
        return topic;
    }

    String payload() {
        return payload;
    }

    int attempts() {
        return attempts;
    }
}
