package com.shop.supportservice.knowledge;

import com.shop.supportservice.FakeAiConfiguration;
import com.shop.supportservice.FakeEmbeddingModel;
import com.shop.supportservice.TestcontainersConfiguration;
import com.shop.supportservice.tools.PolicyHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, FakeAiConfiguration.class})
class PolicySearchTest {

    @Autowired
    PolicyIndexer indexer;

    @Autowired
    HybridPolicySearch search;

    @Autowired
    FakeEmbeddingModel embeddings;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void indexPolicies() {
        indexer.indexChanged();
    }

    @Test
    void findsExactTermThatVectorSearchMisses() {
        var vectorOnly = search.searchVectorsOnly("Do you take BLIK?");
        var hybrid = search.search("Do you take BLIK?");

        assertThat(vectorOnly).extracting(PolicyHit::section).allMatch(s -> s.startsWith("delivery#"));
        assertThat(hybrid).hasSize(3).anySatisfy(hit -> assertThat(hit.content()).contains("BLIK"));
    }

    @Test
    void secondRunDoesNotDuplicateChunks() {
        var chunks = countChunks();
        var calls = embeddings.calls();

        indexer.indexChanged();

        assertThat(countChunks()).isEqualTo(chunks).isEqualTo(42);
        assertThat(embeddings.calls()).isEqualTo(calls);
    }

    @Test
    void reindexesOnlyChangedDocument() {
        jdbc.sql("UPDATE policy_documents SET checksum = 'old' WHERE path = 'returns'").update();
        var calls = embeddings.calls();

        indexer.indexChanged();

        assertThat(embeddings.calls()).isEqualTo(calls + 1);
        assertThat(countChunks()).isEqualTo(42);
    }

    private long countChunks() {
        return jdbc.sql("SELECT count(*) FROM policy_chunks").query(Long.class).single();
    }
}
