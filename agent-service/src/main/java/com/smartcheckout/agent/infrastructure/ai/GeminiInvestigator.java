package com.smartcheckout.agent.infrastructure.ai;

import com.smartcheckout.agent.application.InvestigationRequest;
import com.smartcheckout.agent.application.port.Investigator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The AI agent: Gemini (through Spring AI) decides which tools to call, reads the data,
 * then answers with a structured report. Created only when spring.ai.model.chat=google-genai;
 * otherwise the application uses the rule-based fallback.
 * <p>
 * The tool loop (model asks for a tool, Spring AI runs it, sends the result back) is handled by
 * Spring AI, with a hard limit on the number of tool calls (spring.ai.tools.limits).
 * If a model is overloaded or out of quota, the next configured model is tried.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
class GeminiInvestigator implements Investigator {

    private final ChatClient chatClient;
    private final ReadOnlyServices services;
    private final List<String> models;

    GeminiInvestigator(ChatClient.Builder chatClientBuilder, ReadOnlyServices services,
                       @Value("classpath:prompts/investigator-system.md") Resource systemPrompt,
                       @Value("${smartcheckout.agent.models}") List<String> models) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
        this.services = services;
        this.models = models.stream().map(String::strip).filter(model -> !model.isEmpty()).distinct().toList();
    }

    @Override
    public Result investigate(InvestigationRequest request) {
        // One trace for all attempts: tools called before a model failed were real reads too.
        InvestigationTools tools = new InvestigationTools(services);
        return ModelFallback.firstAvailable(models, model -> {
            AgentAnswer answer = chatClient.prompt()
                    .options(GoogleGenAiChatOptions.builder().model(model))
                    .user(userMessage(request))
                    .tools(tools)
                    .call()
                    .entity(AgentAnswer.class);
            return new Result(AgentAnswer.toReport(answer), tools.calls(), model);
        });
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
