package com.shop.supportservice.knowledge;

import com.shop.supportservice.tools.PolicyHit;
import com.shop.supportservice.tools.PolicySearch;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vector search finds paraphrases ("send it back" for returns), full-text search finds exact words
 * like "BLIK" or "Paczkomat" that embeddings blur. The two rankings are merged with Reciprocal Rank Fusion.
 */
@Component
public class HybridPolicySearch implements PolicySearch {

    static final int RESULTS = 3;
    private static final int CANDIDATES = 10;
    // The usual RRF constant: damps the difference between rank 1 and rank 2 so that one list cannot dominate.
    private static final int RRF_K = 60;

    private final VectorStore vectorStore;
    private final JdbcClient jdbc;

    public HybridPolicySearch(VectorStore vectorStore, JdbcClient jdbc) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    @Override
    public List<PolicyHit> search(String query) {
        var vector = vectorCandidates(query);
        var text = textCandidates(query);

        var scores = new HashMap<String, Double>();
        addRanks(scores, vector.keySet().stream().toList());
        addRanks(scores, text.keySet().stream().toList());
        var content = new HashMap<>(text);
        content.putAll(vector);

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(RESULTS)
                .map(e -> new PolicyHit(e.getKey(), content.get(e.getKey())))
                .toList();
    }

    public List<PolicyHit> searchVectorsOnly(String query) {
        return vectorCandidates(query).entrySet().stream()
                .limit(RESULTS)
                .map(e -> new PolicyHit(e.getKey(), e.getValue()))
                .toList();
    }

    private Map<String, String> vectorCandidates(String query) {
        var result = new LinkedHashMap<String, String>();
        for (Document doc : vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(CANDIDATES).build())) {
            result.putIfAbsent((String) doc.getMetadata().get("section"), doc.getText());
        }
        return result;
    }

    // plainto_tsquery joins words with AND, which fails for a question in Polish with one English word in it.
    // Joining them with OR lets a single exact term match; ts_rank_cd still prefers chunks with more of them.
    private Map<String, String> textCandidates(String query) {
        var result = new LinkedHashMap<String, String>();
        jdbc.sql("""
                        SELECT section, content
                        FROM policy_chunks, to_tsquery('simple', replace(plainto_tsquery('english', :q)::text, '&', '|')) AS q
                        WHERE content_tsv @@ q
                        ORDER BY ts_rank_cd(content_tsv, q) DESC
                        LIMIT :limit
                        """)
                .param("q", query)
                .param("limit", CANDIDATES)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2)))
                .list()
                .forEach(e -> result.putIfAbsent(e.getKey(), e.getValue()));
        return result;
    }

    private static void addRanks(Map<String, Double> scores, List<String> ranked) {
        for (int i = 0; i < ranked.size(); i++) {
            scores.merge(ranked.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }
}
