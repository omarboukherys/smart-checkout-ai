package com.smartcheckout.agent.application.port;

import com.smartcheckout.agent.domain.Investigation;

import java.util.List;
import java.util.Optional;

public interface InvestigationStore {

    Optional<Investigation> findByCheckoutId(String checkoutId);

    /** Inserts or replaces the investigation of a checkout. */
    void save(Investigation investigation);

    List<Investigation> findRecent(int limit);
}
