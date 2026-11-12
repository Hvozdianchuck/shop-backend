package com.shop.supportservice.eval;

import com.shop.supportservice.TestcontainersConfiguration;
import com.shop.supportservice.knowledge.HybridPolicySearch;
import com.shop.supportservice.knowledge.PolicyIndexer;
import com.shop.supportservice.tools.PolicyHit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Recall@3 of the policy search on its own, vectors only against hybrid. If search misses the section,
 * no prompt can fix the answer, so this is measured separately from the model.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RetrievalEval {

    private static final Logger log = LoggerFactory.getLogger(RetrievalEval.class);

    @Autowired
    PolicyIndexer indexer;

    @Autowired
    HybridPolicySearch search;

    @Autowired
    JsonMapper json;

    @Test
    void compareVectorAndHybridSearch() throws IOException {
        indexer.indexChanged();
        var questions = EvalData.read("retrieval.jsonl", Question.class, json);

        var out = new StringBuilder("| id | query | expected | vectors | hybrid |\n| --- | --- | --- | --- | --- |\n");
        int vectorHits = 0;
        int hybridHits = 0;
        for (var q : questions) {
            var vector = sections(search.searchVectorsOnly(q.query()));
            var hybrid = sections(search.search(q.query()));
            vectorHits += vector.contains(q.section()) ? 1 : 0;
            hybridHits += hybrid.contains(q.section()) ? 1 : 0;
            out.append("| %s | %s | %s | %s | %s |\n".formatted(q.id(), q.query(), q.section(),
                    mark(vector, q.section()), mark(hybrid, q.section())));
        }
        var summary = "Recall@3 on %d questions: vectors only %d/%d, hybrid %d/%d\n\n".formatted(
                questions.size(), vectorHits, questions.size(), hybridHits, questions.size());

        Files.createDirectories(EvalData.RESULTS);
        Files.writeString(EvalData.RESULTS.resolve(EvalData.timestamp() + "-retrieval.md"), summary + out, UTF_8);
        log.info("\n{}{}", summary, out);
    }

    private static List<String> sections(List<PolicyHit> hits) {
        return hits.stream().map(PolicyHit::section).toList();
    }

    private static String mark(List<String> found, String expected) {
        return (found.contains(expected) ? "hit " : "miss ") + String.join(", ", found);
    }

    record Question(String id, String query, String section) {
    }
}
