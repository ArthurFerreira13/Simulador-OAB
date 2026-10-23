-- V20 — Tabela de embeddings do Ollama (nomic-embed-text, 768 dims).
--
-- Layout compatível com PgVectorEmbeddingStore (LangChain4j):
--   embedding_id (UUID PK), embedding (vector), text (TEXT), metadata (JSONB).
--
-- Separada das tabelas do Gemini e do Voyage. Cada modelo gera vetores
-- em espaço vetorial diferente — não podem ser misturados na mesma busca.

CREATE TABLE comentario_embedding_ollama (
                                             embedding_id UUID         PRIMARY KEY,
                                             embedding    vector(768)  NOT NULL,
                                             text         TEXT         NOT NULL,
                                             metadata     JSONB
);

CREATE INDEX idx_emb_ollama_cosine
    ON comentario_embedding_ollama
    USING hnsw (embedding vector_cosine_ops);