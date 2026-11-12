package com.shop.supportservice.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Embeds policy files that are new or changed since the last run. It also runs on a schedule,
 * so policies get indexed once Ollama is back if it was down at startup.
 */
@Component
public class PolicyIndexer {

    private static final Logger log = LoggerFactory.getLogger(PolicyIndexer.class);

    private final ResourcePatternResolver resources;
    private final PolicySplitter splitter;
    private final VectorStore vectorStore;
    private final JdbcClient jdbc;
    private final TransactionOperations tx;

    public PolicyIndexer(ResourcePatternResolver resources, PolicySplitter splitter, VectorStore vectorStore,
                         JdbcClient jdbc, TransactionOperations tx) {
        this.resources = resources;
        this.splitter = splitter;
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${shop.policies.reindex-interval:5m}")
    public synchronized void indexChanged() {
        var files = readPolicyFiles();
        var known = jdbc.sql("SELECT path, checksum FROM policy_documents")
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2)))
                .list().stream()
                .collect(HashMap<String, String>::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);

        files.forEach((document, markdown) -> {
            var checksum = sha256(markdown);
            if (checksum.equals(known.get(document))) {
                return;
            }
            try {
                tx.executeWithoutResult(status -> reindex(document, markdown, checksum));
                log.info("Indexed policy {}", document);
            } catch (RuntimeException e) {
                log.warn("Could not index policy {}, will retry: {}", document, e.getMessage());
            }
        });

        known.keySet().stream().filter(document -> !files.containsKey(document)).forEach(document ->
                tx.executeWithoutResult(status -> {
                    deleteChunks(document);
                    jdbc.sql("DELETE FROM policy_documents WHERE path = ?").param(document).update();
                }));
    }

    private void reindex(String document, String markdown, String checksum) {
        deleteChunks(document);
        vectorStore.add(splitter.split(document, markdown));
        jdbc.sql("""
                        INSERT INTO policy_documents (path, checksum, indexed_at) VALUES (?, ?, now())
                        ON CONFLICT (path) DO UPDATE SET checksum = excluded.checksum, indexed_at = now()
                        """)
                .params(document, checksum)
                .update();
    }

    private void deleteChunks(String document) {
        jdbc.sql("DELETE FROM policy_chunks WHERE document = ?").param(document).update();
    }

    private Map<String, String> readPolicyFiles() {
        try {
            var files = new HashMap<String, String>();
            for (Resource resource : resources.getResources("classpath:policies/*.md")) {
                var document = resource.getFilename().replaceFirst("\\.md$", "");
                files.put(document, resource.getContentAsString(StandardCharsets.UTF_8));
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
