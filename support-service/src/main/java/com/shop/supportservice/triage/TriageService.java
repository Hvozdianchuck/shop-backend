package com.shop.supportservice.triage;

import com.shop.supportservice.tools.OrderLookup;
import com.shop.supportservice.tools.OrderTools;
import com.shop.supportservice.tools.PolicySearch;
import com.shop.supportservice.tools.PolicyTools;
import com.shop.supportservice.tools.ToolJournal;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * One ticket, one short workflow: the model may call read-only tools a few times, then is asked for
 * a {@link TriageResult}. The tool loop is driven here rather than by ChatClient's tool advisor, so the number
 * of calls is capped and every tool result is recorded for the checks that follow.
 */
@Service
public class TriageService {

    static final int MAX_TOOL_CALLS = 3;
    // Enough for a tool call (about 65 tokens); the free-text answer of this phase is only a draft for the JSON.
    static final int TOOL_PHASE_MAX_TOKENS = 96;
    private static final String FINAL_ANSWER = "Now write the final answer for the support agent as described in the instructions.";
    private static final int MAX_ERROR_LENGTH = 300;

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final OrderLookup orders;
    private final PolicySearch policies;
    private final PromptLibrary prompts;
    private final ResultValidator validator;
    private final HandoffPolicy handoffPolicy;
    private final CircuitBreaker circuitBreaker;
    private final BeanOutputConverter<TriageResult> converter = new BeanOutputConverter<>(TriageResult.class);

    public TriageService(ChatModel chatModel, ToolCallingManager toolCallingManager, OrderLookup orders,
                         PolicySearch policies, PromptLibrary prompts, ResultValidator validator,
                         HandoffPolicy handoffPolicy, CircuitBreakerRegistry circuitBreakers) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.orders = orders;
        this.policies = policies;
        this.prompts = prompts;
        this.validator = validator;
        this.handoffPolicy = handoffPolicy;
        this.circuitBreaker = circuitBreakers.circuitBreaker("model");
    }

    public TriageDecision triage(String text) {
        return triage(text, prompts.activeVersion());
    }

    public TriageDecision triage(String text, String promptVersion) {
        var started = System.nanoTime();
        var journal = new ToolJournal();
        var call = new ModelCall();
        // Options in a prompt replace the configured ones instead of being merged, so start from the defaults.
        var defaults = (ToolCallingChatOptions) chatModel.getDefaultOptions();
        var withTools = defaults.mutate()
                .toolCallbacks(ToolCallbacks.from(new OrderTools(orders, journal), new PolicyTools(policies, journal)))
                .maxTokens(TOOL_PHASE_MAX_TOKENS)
                .build();
        var withoutTools = defaults.mutate().build();

        List<Message> messages = new ArrayList<>(List.of(
                new SystemMessage(prompts.systemPrompt(promptVersion)),
                new UserMessage("<customer_message>\n" + text + "\n</customer_message>")));
        var response = call.send(messages, withTools);
        var toolCalls = 0;
        var finalRequest = FINAL_ANSWER;
        while (response.hasToolCalls()) {
            var requested = response.getResult().getOutput().getToolCalls().size();
            if (toolCalls + requested > MAX_TOOL_CALLS) {
                finalRequest = "No more tool calls are allowed. " + FINAL_ANSWER;
                break;
            }
            toolCalls += requested;
            var executed = toolCallingManager.executeToolCalls(new Prompt(messages, withTools), response);
            messages = new ArrayList<>(executed.conversationHistory());
            response = call.send(messages, withTools);
        }
        if (!response.hasToolCalls()) {
            messages.add(response.getResult().getOutput());
        }

        // The JSON format goes in only now. With the schema in the first request, the model answers with JSON
        // straight away and never calls a tool (measured: 0 tool calls on 40 tickets).
        messages.add(new UserMessage(finalRequest + "\n" + converter.getFormat()));
        response = call.send(messages, withoutTools);

        var parsed = parse(response);
        var retried = parsed.result() == null;
        if (retried) {
            messages.add(response.getResult().getOutput());
            messages.add(new UserMessage("Your reply could not be read: " + parsed.error()
                    + "\nReply again with only the JSON object described in the instructions."));
            parsed = parse(call.send(messages, withoutTools));
        }

        var result = parsed.result();
        var problems = result == null ? List.<String>of() : validator.validate(result, journal);
        return new TriageDecision(result, journal, handoffPolicy.decide(result, problems, journal), promptVersion,
                call.model, call.inputTokens, call.outputTokens, (System.nanoTime() - started) / 1_000_000, retried);
    }

    private Parsed parse(ChatResponse response) {
        try {
            return new Parsed(converter.convert(response.getResult().getOutput().getText()), null);
        } catch (RuntimeException e) {
            var error = String.valueOf(e.getMessage());
            return new Parsed(null, error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error);
        }
    }

    private record Parsed(TriageResult result, String error) {
    }

    private class ModelCall {

        String model;
        int inputTokens;
        int outputTokens;

        ChatResponse send(List<Message> messages, ChatOptions options) {
            var response = circuitBreaker.executeSupplier(() -> chatModel.call(new Prompt(messages, options)));
            var metadata = response.getMetadata();
            model = metadata.getModel();
            inputTokens += metadata.getUsage().getPromptTokens();
            outputTokens += metadata.getUsage().getCompletionTokens();
            return response;
        }
    }
}
