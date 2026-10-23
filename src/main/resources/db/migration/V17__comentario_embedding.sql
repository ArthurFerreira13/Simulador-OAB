-- Módulo: questoes
-- Tabela de embeddings dos comentários (RAG).
--
-- Cada linha guarda o vetor de 1536 dimensões do comentário de uma
-- alternativa. O RAG busca por similaridade de cosseno.
--
-- pgvector já foi habilitado na V3.

CREATE TABLE comentario_embedding (
                                      id               BIGSERIAL PRIMARY KEY,
                                      alternativa_id   BIGINT    NOT NULL UNIQUE,
                                      embedding        vector(1536) NOT NULL,
                                      modelo           VARCHAR(60) NOT NULL,
                                      criado_em        TIMESTAMP NOT NULL DEFAULT NOW(),

                                      CONSTRAINT fk_comentario_embedding_alternativa
                                          FOREIGN KEY (alternativa_id) REFERENCES alternativas (id) ON DELETE CASCADE
);

-- Índice IVFFlat para busca por similaridade de cosseno.
-- lists=100 é adequado para até ~1M vetores.
CREATE INDEX idx_comentario_embedding_cosine
    ON comentario_embedding
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);