package com.smartcheckout.checkout.application.port;

import java.util.UUID;

/**
 * Output port, the "inbox": remembers which incoming events were already handled.
 * Recorded in the same transaction as the state change, so an event is applied exactly once.
 */
public interface ProcessedEvents {

    /** @return true the first time this event id is seen, false for a duplicate delivery */
    boolean markProcessed(UUID eventId);
}
