package com.shop.supportservice;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Answers prompts from a script, in order, and remembers every prompt it got.
 */
public class ScriptedChatModel implements ChatModel {

    private final Queue<Supplier<AssistantMessage>> script = new ConcurrentLinkedQueue<>();
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();

    public ScriptedChatModel reply(String text) {
        script.add(() -> new AssistantMessage(text));
        return this;
    }

    public ScriptedChatModel callTool(String name, String arguments) {
        var call = new AssistantMessage.ToolCall(UUID.randomUUID().toString(), "function", name, arguments);
        script.add(() -> AssistantMessage.builder().content("").toolCalls(List.of(call)).build());
        return this;
    }

    public ScriptedChatModel fail(RuntimeException e) {
        script.add(() -> {
            throw e;
        });
        return this;
    }

    public void reset() {
        script.clear();
        prompts.clear();
    }

    public List<Prompt> prompts() {
        return prompts;
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().model("scripted").build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        var next = script.poll();
        if (next == null) {
            throw new IllegalStateException("No scripted reply left");
        }
        var metadata = ChatResponseMetadata.builder().model("scripted").usage(new DefaultUsage(100, 20)).build();
        return new ChatResponse(List.of(new Generation(next.get())), metadata);
    }
}
