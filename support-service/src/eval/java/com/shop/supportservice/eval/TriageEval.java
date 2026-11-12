package com.shop.supportservice.eval;

import com.shop.supportservice.TestcontainersConfiguration;
import com.shop.supportservice.knowledge.PolicyIndexer;
import com.shop.supportservice.privacy.PiiMasker;
import com.shop.supportservice.triage.TriageService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * Runs the dataset through the real model: masking, tool calls, checks and the handoff decision, as in production.
 * <p>
 * {@code ./mvnw verify -Peval -pl support-service -Dtest=TriageEval -Deval.split=work -Deval.prompts=v1,v2 -Deval.runs=1}
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TriageEval.Fixtures.class})
class TriageEval {

    private static final Logger log = LoggerFactory.getLogger(TriageEval.class);

    @Autowired
    TriageService triage;

    @Autowired
    PiiMasker masker;

    @Autowired
    PolicyIndexer indexer;

    @Autowired
    FixtureOrders orders;

    @Autowired
    JsonMapper json;

    @Value("${spring.ai.ollama.chat.model}")
    String model;

    @Test
    void evaluate() throws IOException {
        var split = System.getProperty("eval.split", "work");
        var versions = List.of(System.getProperty("eval.prompts", "v3").split(","));
        var runs = Integer.getInteger("eval.runs", 1);
        var ids = Set.of(System.getProperty("eval.ids", "").split(","));
        var cases = EvalData.read("dataset.jsonl", EvalCase.class, json).stream()
                .filter(c -> c.split().equals(split))
                .filter(c -> ids.equals(Set.of("")) || ids.contains(c.id()))
                .toList();
        cases.stream().filter(c -> c.order() != null).forEach(c -> orders.add(c.order()));
        indexer.indexChanged();

        var scorer = new Scorer();
        var stamp = EvalData.timestamp();
        var modelName = model.replace(':', '-');
        var all = new ArrayList<CaseScore>();
        var total = versions.size() * runs * cases.size();
        Files.createDirectories(EvalData.RESULTS);
        for (var version : versions) {
            for (int run = 1; run <= runs; run++) {
                var file = EvalData.RESULTS.resolve("%s-%s-%s-%s-run%d.jsonl".formatted(stamp, split, version, modelName, run));
                for (var c : cases) {
                    CaseScore score;
                    try {
                        score = scorer.score(c, triage.triage(masker.mask(c.text()), version), run);
                    } catch (RuntimeException e) {
                        score = Scorer.failed(c, version, run, e);
                    }
                    all.add(score);
                    Files.writeString(file, json.writeValueAsString(score) + "\n", UTF_8, CREATE, APPEND);
                    log.info("[{}/{}] {} {} run {}: intent {}, handoff {}, {} ms{}", all.size(), total, version, c.id(), run,
                            score.intent(), score.handoff(), score.latencyMs(), score.error() == null ? "" : ", " + score.error());
                }
            }
        }

        var report = "Model %s, %s set, %s\n\n%s".formatted(model, split, stamp, EvalReport.table(all));
        Files.writeString(EvalData.RESULTS.resolve("%s-%s-%s.md".formatted(stamp, split, modelName)), report, UTF_8);
        log.info("\n{}", report);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {

        @Bean
        @Primary
        FixtureOrders fixtureOrders() {
            return new FixtureOrders();
        }
    }
}
