package com.smartcheckout.risk.application.port;

import com.smartcheckout.risk.application.StoredAssessment;

/**
 * Output port: announces a risk decision. Must fail loudly if the event cannot be published,
 * so that the incoming message is retried instead of being acknowledged.
 */
public interface RiskEventPublisher {

    void publish(StoredAssessment assessment);
}
