package com.smartcheckout.risk.infrastructure.web;

import com.smartcheckout.risk.application.RiskAssessmentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only access to the decisions, used by the back-office and later by the AI agent.
 */
@RestController
@RequestMapping("/api/v1/risk-assessments")
class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;

    RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    @GetMapping("/{checkoutId}")
    ResponseEntity<RiskAssessmentResponse> get(@PathVariable String checkoutId) {
        return riskAssessmentService.find(checkoutId)
                .map(RiskAssessmentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Recent payments of a customer: amounts, channels, countries, devices and past decisions. */
    @GetMapping
    List<RiskAssessmentResponse> history(@RequestParam String customerId,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return riskAssessmentService.history(customerId, limit).stream()
                .map(RiskAssessmentResponse::from)
                .toList();
    }
}
