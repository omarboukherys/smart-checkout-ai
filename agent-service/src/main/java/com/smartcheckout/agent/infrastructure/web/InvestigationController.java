package com.smartcheckout.agent.infrastructure.web;

import com.smartcheckout.agent.application.InvestigationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Investigation reports for the back-office. Reading only, plus a "run again" action:
 * the approve/reject decision stays on checkout-service, taken by a human.
 */
@RestController
@RequestMapping("/api/v1/investigations")
class InvestigationController {

    private final InvestigationService investigationService;

    InvestigationController(InvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    @GetMapping("/{checkoutId}")
    ResponseEntity<InvestigationResponse> get(@PathVariable String checkoutId) {
        return investigationService.find(checkoutId)
                .map(InvestigationResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    List<InvestigationResponse> recent(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return investigationService.recent(limit).stream()
                .map(InvestigationResponse::from)
                .toList();
    }

    /** Runs a new investigation (for example after the customer was called back). 404 if the checkout is unknown. */
    @PostMapping("/{checkoutId}")
    ResponseEntity<InvestigationResponse> reinvestigate(@PathVariable String checkoutId) {
        return investigationService.reinvestigate(checkoutId)
                .map(InvestigationResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** risk-service down or too slow while reading the decision to re-investigate. */
    @ExceptionHandler(RestClientException.class)
    ProblemDetail dependencyUnavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The risk service is unavailable, try again later");
    }
}
