CREATE EXTENSION IF NOT EXISTS vector;

-- id, content, metadata and embedding are the columns Spring AI's PgVectorStore writes;
-- the generated columns are ours, for filtering and full-text search.
CREATE TABLE policy_chunks (
    id          UUID PRIMARY KEY,
    content     TEXT         NOT NULL,
    metadata    JSON         NOT NULL,
    embedding   VECTOR(1024) NOT NULL,
    document    TEXT GENERATED ALWAYS AS (metadata ->> 'document') STORED,
    section     TEXT GENERATED ALWAYS AS (metadata ->> 'section') STORED,
    content_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', content)) STORED
);

CREATE INDEX policy_chunks_embedding_idx ON policy_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX policy_chunks_tsv_idx ON policy_chunks USING gin (content_tsv);
CREATE INDEX policy_chunks_document_idx ON policy_chunks (document);

CREATE TABLE policy_documents (
    path       VARCHAR(255) PRIMARY KEY,
    checksum   VARCHAR(64)  NOT NULL,
    indexed_at TIMESTAMPTZ  NOT NULL
);
