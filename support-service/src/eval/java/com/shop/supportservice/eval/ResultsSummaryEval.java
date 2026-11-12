package com.shop.supportservice.eval;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Builds the table from result files of earlier runs, for example runs that were split or resumed:
 * {@code -Dtest=ResultsSummaryEval -Deval.files=20261112-1700-control-v1-qwen3-8b-run1.jsonl,...}
 */
class ResultsSummaryEval {

    private static final Logger log = LoggerFactory.getLogger(ResultsSummaryEval.class);

    @Test
    void summarise() throws IOException {
        assumeTrue(System.getProperty("eval.files") != null, "set -Deval.files to summarise saved runs");
        var json = JsonMapper.builder().build();
        var scores = new ArrayList<CaseScore>();
        // Each file is one run; files from separate invocations all say run 1.
        var runsPerVersion = new HashMap<String, Integer>();
        for (var file : System.getProperty("eval.files").split(",")) {
            var rows = Files.readAllLines(EvalData.RESULTS.resolve(file.strip())).stream()
                    .filter(line -> !line.isBlank())
                    .map(line -> json.readValue(line, CaseScore.class))
                    .toList();
            var run = runsPerVersion.merge(rows.getFirst().promptVersion(), 1, Integer::sum);
            rows.forEach(row -> scores.add(row.withRun(run)));
        }
        var title = System.getProperty("eval.title", "Summary");
        var report = "%s\n\nFiles: %s\n\n%s".formatted(title, Arrays.toString(System.getProperty("eval.files").split(",")),
                EvalReport.table(scores));
        var out = System.getProperty("eval.out");
        if (out != null) {
            Files.writeString(EvalData.RESULTS.resolve(out), report, UTF_8);
        }
        log.info("\n{}", report);
    }
}
