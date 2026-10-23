-- V19 — Alinha comentario_embedding_gemini ao layout do PgVectorEmbeddingStore
-- (LangChain4j): embedding_id, embedding, text, metadata.
-- O vínculo com a alternativa fica em metadata->>'alternativaId'.
-- A tabela das V17/V18 (id, alternativa_id, modelo, criado_em) nunca recebeu
-- dados, então pode ser recriada.

DROP TABLE IF EXISTS comentario_embedding_gemini;

CREATE TABLE comentario_embedding_gemini (
                                             embedding_id UUID         PRIMARY KEY,
                                             embedding    vector(768)  NOT NULL,
                                             text         TEXT         NOT NULL,
                                             metadata     JSONB
);

CREATE INDEX idx_emb_gemini_cosine
    ON comentario_embedding_gemini
    USING hnsw (embedding vector_cosine_ops);