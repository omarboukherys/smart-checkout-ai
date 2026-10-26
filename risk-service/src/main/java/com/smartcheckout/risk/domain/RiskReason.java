package com.smartcheckout.risk.domain;

/**
 * One rule that matched: a stable code (for screens and statistics), its points and a readable detail.
 */
public record RiskReason(String code, int points, String detail) {
}
