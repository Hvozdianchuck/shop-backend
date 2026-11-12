package com.shop.supportservice.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cuts a policy file into one chunk per "##" section, so that a rule is never split in the middle.
 */
@Component
public class PolicySplitter {

    // About 400 tokens of English text.
    static final int MAX_CHARS = 1600;

    public List<Document> split(String document, String markdown) {
        var title = document;
        var chunks = new ArrayList<Document>();
        String heading = null;
        var body = new StringBuilder();
        for (var line : markdown.lines().toList()) {
            if (line.startsWith("# ")) {
                title = line.substring(2).strip();
            } else if (line.startsWith("## ")) {
                addSection(chunks, document, title, heading, body);
                heading = line.substring(3).strip();
                body.setLength(0);
            } else if (heading != null) {
                body.append(line).append('\n');
            }
        }
        addSection(chunks, document, title, heading, body);
        return chunks;
    }

    private void addSection(List<Document> chunks, String document, String title, String heading, StringBuilder body) {
        if (heading == null) {
            return;
        }
        var section = document + "#" + slug(heading);
        var parts = splitLongText(body.toString().strip());
        for (int i = 0; i < parts.size(); i++) {
            var id = parts.size() == 1 ? section : section + "/" + (i + 1);
            chunks.add(Document.builder()
                    .id(UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString())
                    .text(title + " - " + heading + "\n\n" + parts.get(i))
                    .metadata(Map.of("document", document, "section", section))
                    .build());
        }
    }

    private List<String> splitLongText(String text) {
        if (text.length() <= MAX_CHARS) {
            return List.of(text);
        }
        var parts = new ArrayList<String>();
        var current = new StringBuilder();
        for (var paragraph : text.split("\n\\s*\n")) {
            if (!current.isEmpty() && current.length() + paragraph.length() > MAX_CHARS) {
                parts.add(current.toString().strip());
                current.setLength(0);
            }
            current.append(paragraph).append("\n\n");
        }
        parts.add(current.toString().strip());
        return parts;
    }

    static String slug(String heading) {
        return heading.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
