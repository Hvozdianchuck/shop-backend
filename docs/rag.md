# RAG

## Embeddings and semantic search
- An embedding model turns text into a vector; texts with similar meaning get vectors that are close
  (cosine distance here).
- `bge-m3` via Ollama: multilingual, 1024 dimensions. Needed because questions come in three languages and the
  policies are written in English. https://ollama.com/library/bge-m3

## Chunking
- Search and the prompt need small relevant pieces, not whole documents.
- Policies are cut by `##` sections (`PolicySplitter`), one rule per chunk, with the document title in front
  ("Returns - Refund time"), so a chunk still makes sense alone. Fixed-size chunks would cut a rule in half.
- Section ids like `returns#refund-time` are stable and become the `sources` the model must cite.
- A section over ~400 tokens would be split by paragraphs; none of ours is that long.

## pgvector
- Postgres extension: `vector(1024)` column, distance operators, indexes. Image `pgvector/pgvector:0.8.7-pg18`.
- HNSW index (`USING hnsw (embedding vector_cosine_ops)`): approximate nearest neighbours, good recall without
  training, more memory than IVFFlat. https://github.com/pgvector/pgvector#hnsw
- Spring AI `PgVectorStore` writes and searches the table; the table itself comes from Flyway
  (`V2__create_policy_chunks.sql`) with extra generated columns, so `initialize-schema` is off.

## Indexing
- At startup and every 5 minutes, only for files whose SHA-256 changed (`policy_documents`). Per document in one
  transaction: delete old chunks, embed and insert new ones, store the checksum. A restart embeds nothing.
- Runs on a schedule rather than once, so policies get indexed after Ollama comes back if it was down at start.

## Hybrid search
- Vectors blur exact terms: "BLIK", "Paczkomat", "NIP" have no useful meaning for the embedding.
  Full-text search finds them but misses paraphrases.
- Both run (`HybridPolicySearch`), top 10 each, merged with Reciprocal Rank Fusion:
  score = sum of 1 / (60 + rank). Needs no score normalisation between the two lists.
- Full-text query: `plainto_tsquery` ANDs words, which fails for a Polish question with one English term,
  so the terms are OR-ed and `ts_rank_cd` orders by how many match.

## Evaluate search on its own
- If search does not return the right section, no prompt fixes the answer. So recall@3 is measured separately:
  `RetrievalEval` over `eval/retrieval.jsonl` (25 questions, uk/pl/en), vectors only vs hybrid.
