package com.smartcheckout.checkout.infrastructure.web;

import com.smartcheckout.checkout.application.ReviewDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Decision of a human reviewer on a checkout held by the risk engine.
 */
public record ReviewRequest(
        @NotNull ReviewDecision decision,
        @NotBlank @Size(max = 64) String reviewer,
        @Size(max = 500) String comment
) {
}
