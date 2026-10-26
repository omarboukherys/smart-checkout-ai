package com.smartcheckout.agent.application.port;

import com.smartcheckout.agent.application.InvestigationRequest;
import com.smartcheckout.agent.domain.InvestigationReport;
import com.smartcheckout.agent.domain.ToolCall;

import java.util.List;

/**
 * Output port to the AI agent (LLM + tools). Implemented with Spring AI and Gemini.
 * May throw on any failure: the application layer then falls back to the rules.
 */
public interface Investigator {

    Result investigate(InvestigationRequest request);

    /** Model name, kept in the audit trail. */
    String model();

    /**
     * @param report    the agent's answer, already validated by the domain
     * @param toolCalls the tools the agent decided to call, in order
     */
    record Result(InvestigationReport report, List<ToolCall> toolCalls) {
    }
}
