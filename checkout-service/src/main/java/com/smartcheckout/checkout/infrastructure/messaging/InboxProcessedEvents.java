package com.smartcheckout.checkout.infrastructure.messaging;

import com.smartcheckout.checkout.application.port.ProcessedEvents;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Inbox table. "ON CONFLICT DO NOTHING" makes the check and the insert one atomic statement:
 * no race between two consumers receiving the same event.
 */
@Component
class InboxProcessedEvents implements ProcessedEvents {

    private final EntityManager entityManager;

    InboxProcessedEvents(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean markProcessed(UUID eventId) {
        int inserted = entityManager.createNativeQuery("""
                        INSERT INTO processed_event (event_id, processed_at)
                        VALUES (:eventId, now())
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .setParameter("eventId", eventId)
                .executeUpdate();
        return inserted == 1;
    }
}
