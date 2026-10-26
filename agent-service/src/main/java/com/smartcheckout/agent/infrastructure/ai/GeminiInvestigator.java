package com.smartcheckout.agent.infrastructure.ai;

import com.smartcheckout.agent.application.InvestigationRequest;
import com.smartcheckout.agent.application.port.Investigator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * The AI agent: Gemini (through Spring AI) decides which tools to call, reads the data,
 * then answers with a structured report. Created only when spring.ai.model.chat=google-genai;
 * otherwise the application uses the rule-based fallback.
 * <p>
 * The tool loop (model asks for a tool, Spring AI runs it, sends the result back) is handled by
 * Spring AI, with a hard limit on the number of tool calls (spring.ai.tools.limits).
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
class GeminiInvestigator implements Investigator {

    private final ChatClient chatClient;
    private final ReadOnlyServices services;
    private final String model;

    GeminiInvestigator(ChatClient.Builder chatClientBuilder, ReadOnlyServices services,
                       @Value("classpath:prompts/investigator-system.md") Resource systemPrompt,
                       @Value("${spring.ai.google.genai.chat.model:gemini-3.8-flash}") String model) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
        this.services = services;
        this.model = model;
    }

    @Override
    public Result investigate(InvestigationRequest request) {
        InvestigationTools tools = new InvestigationTools(services);
        AgentAnswer answer = chatClient.prompt()
                .user(userMessage(request))
                .tools(tools)
                .call()
                .entity(AgentAnswer.class);
        return new Result(AgentAnswer.toReport(answer), tools.calls());
    }

    @Override
    public String model() {
        return model;
    }

    /** Only identifiers and codes from our own events: no free text from a customer reaches the prompt. */
    static String userMessage(InvestigationRequest request) {
        return """
                A payment was held for review by the risk engine.
                checkoutId: %s
                risk score: %d / 100
                rules that matched: %s

                Investigate with the tools, then return your report.
                """.formatted(request.checkoutId(), request.riskScore(),
                request.reasonCodes().isEmpty() ? "none" : String.join(", ", request.reasonCodes()));
    }
}
