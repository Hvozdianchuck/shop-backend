package com.shop.supportservice.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicySplitterTest {

    private final PolicySplitter splitter = new PolicySplitter();

    @Test
    void makesOneChunkPerSectionWithDocumentTitle() {
        var chunks = splitter.split("returns", """
                # Returns

                ## Return window
                You can return any product within 14 days.

                ## Refund time
                We refund within 14 days.
                """);

        assertThat(chunks).extracting(d -> d.getMetadata().get("section"))
                .containsExactly("returns#return-window", "returns#refund-time");
        assertThat(chunks.getFirst().getText()).isEqualTo("Returns - Return window\n\nYou can return any product within 14 days.");
    }

    @Test
    void keepsTheSameIdForTheSameSection() {
        var markdown = "# Returns\n\n## Return window\nText.\n";

        assertThat(splitter.split("returns", markdown).getFirst().getId())
                .isEqualTo(splitter.split("returns", markdown + "\nMore text.\n").getFirst().getId());
    }

    @Test
    void splitsLongSectionByParagraphs() {
        var paragraph = "word ".repeat(200).strip();
        var chunks = splitter.split("delivery", "# Delivery\n\n## Times\n" + paragraph + "\n\n" + paragraph + "\n");

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(c -> assertThat(c.getMetadata().get("section")).isEqualTo("delivery#times"));
        assertThat(chunks).extracting(c -> c.getId()).doesNotHaveDuplicates();
    }
}
