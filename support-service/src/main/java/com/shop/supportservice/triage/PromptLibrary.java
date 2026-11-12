package com.shop.supportservice.triage;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PromptLibrary {

    private final ResourceLoader resources;
    private final TriageProperties properties;

    public PromptLibrary(ResourceLoader resources, TriageProperties properties) {
        this.resources = resources;
        this.properties = properties;
    }

    public String activeVersion() {
        return properties.promptVersion();
    }

    public String systemPrompt(String version) {
        var resource = resources.getResource("classpath:prompts/triage-" + version + ".st");
        if (!resource.exists()) {
            throw new IllegalArgumentException("Unknown prompt version " + version);
        }
        return PromptTemplate.builder().resource(resource).build().render(Map.of());
    }
}
