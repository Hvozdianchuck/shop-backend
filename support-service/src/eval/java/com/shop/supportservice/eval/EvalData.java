package com.shop.supportservice.eval;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class EvalData {

    static final Path DIR = Path.of("eval");
    static final Path RESULTS = DIR.resolve("results");

    private EvalData() {
    }

    static <T> List<T> read(String file, Class<T> type, JsonMapper json) {
        try {
            return Files.readAllLines(DIR.resolve(file)).stream()
                    .filter(line -> !line.isBlank())
                    .map(line -> json.readValue(line, type))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    }
}
