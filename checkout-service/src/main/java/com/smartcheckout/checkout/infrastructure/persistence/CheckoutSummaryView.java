package com.smartcheckout.checkout.infrastructure.persistence;

import com.smartcheckout.checkout.domain.Channel;
import com.smartcheckout.checkout.domain.CheckoutStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data projection: the list query selects only these columns and never touches basket lines.
 */
public interface CheckoutSummaryView {

    UUID getId();

    String getStoreId();

    Channel getChannel();

    long getTotalMinor();

    String getCurrency();

    CheckoutStatus getStatus();

    Instant getCreatedAt();
}
