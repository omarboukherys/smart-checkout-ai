package com.smartcheckout.agent.domain;

/**
 * One tool used by the agent during an investigation, kept for the audit trail.
 */
public record ToolCall(String tool, String input, long durationMs, boolean succeeded) {
}
