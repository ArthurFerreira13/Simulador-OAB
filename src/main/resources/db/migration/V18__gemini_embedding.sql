-- V18 — Habilita a tabela de embeddings do Gemini.
--
-- A V17 criou `comentario_embedding` (1536 dims, era OpenAI).
-- Como o provedor agora é o Gemini (gemini-embedding-001),
-- essa tabela é renomeada e ajustada para 768 dims.

DROP INDEX IF EXISTS idx_comentario_embedding_cosine;

ALTER TABLE comentario_embedding RENAME TO comentario_embedding_gemini;

ALTER TABLE comentario_embedding_gemini
ALTER COLUMN embedding TYPE vector(768);

ALTER TABLE comentario_embedding_gemini
    RENAME CONSTRAINT fk_comentario_embedding_alternativa
    TO fk_emb_gemini_alt;

ALTER TABLE comentario_embedding_gemini
    RENAME CONSTRAINT comentario_embedding_alternativa_id_key
    TO uk_emb_gemini_alternativa;

CREATE INDEX idx_emb_gemini_cosine
    ON comentario_embedding_gemini
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);