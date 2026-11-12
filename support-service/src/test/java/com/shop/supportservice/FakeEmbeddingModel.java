package com.shop.supportservice;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Puts each text on a single axis. Texts with the same keyword share an axis and are "similar";
 * any other text gets an axis from its hash, so it is unrelated to the rest.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    static final int DIMENSIONS = 1024;

    private final Map<String, Integer> axes = new LinkedHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();

    public FakeEmbeddingModel similar(String keyword, int axis) {
        axes.put(keyword, axis);
        return this;
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        calls.incrementAndGet();
        var embeddings = new ArrayList<Embedding>();
        for (int i = 0; i < request.getInstructions().size(); i++) {
            embeddings.add(new Embedding(vectorFor(request.getInstructions().get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorFor(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] vectorFor(String text) {
        var lower = text.toLowerCase();
        var axis = axes.entrySet().stream()
                .filter(e -> lower.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(1 + Math.floorMod(text.hashCode(), DIMENSIONS - 1));
        var vector = new float[DIMENSIONS];
        vector[axis] = 1;
        return vector;
    }
}
