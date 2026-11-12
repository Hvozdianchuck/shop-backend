package com.shop.supportservice.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Markdown table: one row per prompt version, each cell the mean over runs with the lowest and highest run.
 * A difference smaller than that range is noise, not an improvement.
 */
final class EvalReport {

    private static final Map<String, Function<CaseScore, Boolean>> METRICS = new LinkedHashMap<>();

    static {
        METRICS.put("intent", CaseScore::intent);
        METRICS.put("order id", CaseScore::orderId);
        METRICS.put("tools", CaseScore::tools);
        METRICS.put("status in draft", CaseScore::statusGrounded);
        METRICS.put("policy source", CaseScore::source);
        METRICS.put("reply language", CaseScore::language);
        METRICS.put("attacks resisted", CaseScore::attack);
        METRICS.put("handoff", CaseScore::handoff);
    }

    private EvalReport() {
    }

    static String table(List<CaseScore> scores) {
        var header = new ArrayList<>(List.of("prompt", "runs", "tickets"));
        header.addAll(METRICS.keySet());
        header.addAll(List.of("parsed 1st try", "tokens in", "tokens out", "median s"));
        var out = new StringBuilder("| " + String.join(" | ", header) + " |\n");
        out.append("|").append(" --- |".repeat(header.size())).append("\n");

        var byVersion = scores.stream().collect(Collectors.groupingBy(CaseScore::promptVersion, LinkedHashMap::new, Collectors.toList()));
        byVersion.forEach((version, versionScores) -> {
            var runs = versionScores.stream().collect(Collectors.groupingBy(CaseScore::run)).values();
            var row = new ArrayList<String>();
            row.add(version);
            row.add(String.valueOf(runs.size()));
            row.add(String.valueOf(runs.iterator().next().size()));
            METRICS.values().forEach(metric -> row.add(rate(runs, metric)));
            row.add(rate(runs, s -> s.parsed() && !s.retried()));
            row.add(String.valueOf(Math.round(versionScores.stream().mapToInt(CaseScore::inputTokens).average().orElse(0))));
            row.add(String.valueOf(Math.round(versionScores.stream().mapToInt(CaseScore::outputTokens).average().orElse(0))));
            row.add(String.format(Locale.ROOT, "%.1f", median(versionScores) / 1000.0));
            out.append("| ").append(String.join(" | ", row)).append(" |\n");
        });
        return out.toString();
    }

    private static String rate(Iterable<List<CaseScore>> runs, Function<CaseScore, Boolean> metric) {
        var rates = new ArrayList<Double>();
        for (var run : runs) {
            var applicable = run.stream().map(metric).filter(v -> v != null).toList();
            if (!applicable.isEmpty()) {
                rates.add(100.0 * applicable.stream().filter(v -> v).count() / applicable.size());
            }
        }
        if (rates.isEmpty()) {
            return "-";
        }
        var mean = rates.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        if (rates.size() == 1) {
            return String.format(Locale.ROOT, "%.0f%%", mean);
        }
        return String.format(Locale.ROOT, "%.0f%% (%.0f-%.0f)", mean, rates.stream().min(Comparator.naturalOrder()).orElseThrow(),
                rates.stream().max(Comparator.naturalOrder()).orElseThrow());
    }

    private static double median(List<CaseScore> scores) {
        var sorted = scores.stream().mapToLong(CaseScore::latencyMs).filter(ms -> ms > 0).sorted().toArray();
        if (sorted.length == 0) {
            return 0;
        }
        return sorted.length % 2 == 1 ? sorted[sorted.length / 2] : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
    }
}
