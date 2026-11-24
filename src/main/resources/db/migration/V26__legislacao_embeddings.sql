-- V26 — Fase B do módulo legislacao: armazenamento de embeddings, separado e versionado.
--
-- Um vetor por (versão de dispositivo, modelo, versão do pipeline). Cada provedor/modelo tem a
-- sua tabela: espaços vetoriais diferentes NUNCA são comparados nem misturados (mesma regra
-- das tabelas comentario_embedding_*). Dimensão 768 (Gemini e nomic-embed-text, como no tutor).
--
-- Cada linha registra provedor, modelo, versão do modelo, dimensão, versão do pipeline (chunking e
-- contextualização), hash do texto contextualizado e o próprio texto enviado ao modelo (auditoria
-- e reprocessamento). Embedding não é o dado de verdade: pode ser recriado a partir das versões.
--
-- Pré-requisitos: V25 aplicada; extensão vector (V3).
-- Busca com filtro (ativo, norma) em HNSW pede pgvector >= 0.8 com varredura iterativa
-- (SET LOCAL hnsw.iterative_scan); a triagem do banco informa 0.8.6.

-- ---------------------------------------------------------------------------
-- Gemini (gemini-embedding-001, 768 dimensões)
-- ---------------------------------------------------------------------------
CREATE TABLE legislacao_embedding_gemini (
                                             id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                             versao_dispositivo_id UUID         NOT NULL REFERENCES legislacao_versao_dispositivo (id),
                                             norma_id              UUID         NOT NULL REFERENCES legislacao_norma (id),
                                             provedor              VARCHAR(40)  NOT NULL DEFAULT 'GEMINI',
                                             modelo                VARCHAR(120) NOT NULL,
                                             versao_modelo         VARCHAR(60),
                                             dimensao              INTEGER      NOT NULL DEFAULT 768,
                                             versao_pipeline       VARCHAR(40)  NOT NULL,
                                             hash_contextualizado  CHAR(64)     NOT NULL,
                                             texto_embutido        TEXT         NOT NULL,
                                             embedding             vector(768)  NOT NULL,
                                             ativo                 BOOLEAN      NOT NULL DEFAULT TRUE,
                                             criado_em             TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                             CONSTRAINT uk_leg_emb_gemini_versao UNIQUE (versao_dispositivo_id, modelo, versao_pipeline),
                                             CONSTRAINT ck_leg_emb_gemini_dimensao CHECK (dimensao = 768),
                                             CONSTRAINT ck_leg_emb_gemini_hash CHECK (hash_contextualizado ~ '^[0-9a-f]{64}$')
    );

-- Só vetores ativos entram no índice (versões substituídas saem dele).
-- m e ef_construction são os padrões do pgvector; ajuste só depois do benchmark (scripts/benchmark_embeddings.sql).
CREATE INDEX idx_leg_emb_gemini_hnsw
    ON legislacao_embedding_gemini USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE ativo;

CREATE INDEX idx_leg_emb_gemini_norma ON legislacao_embedding_gemini (norma_id) WHERE ativo;
CREATE INDEX idx_leg_emb_gemini_pendencia ON legislacao_embedding_gemini (modelo, versao_pipeline);

-- ---------------------------------------------------------------------------
-- Ollama (nomic-embed-text, 768 dimensões)
-- ---------------------------------------------------------------------------
CREATE TABLE legislacao_embedding_ollama (
                                             id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                             versao_dispositivo_id UUID         NOT NULL REFERENCES legislacao_versao_dispositivo (id),
                                             norma_id              UUID         NOT NULL REFERENCES legislacao_norma (id),
                                             provedor              VARCHAR(40)  NOT NULL DEFAULT 'OLLAMA',
                                             modelo                VARCHAR(120) NOT NULL,
                                             versao_modelo         VARCHAR(60),
                                             dimensao              INTEGER      NOT NULL DEFAULT 768,
                                             versao_pipeline       VARCHAR(40)  NOT NULL,
                                             hash_contextualizado  CHAR(64)     NOT NULL,
                                             texto_embutido        TEXT         NOT NULL,
                                             embedding             vector(768)  NOT NULL,
                                             ativo                 BOOLEAN      NOT NULL DEFAULT TRUE,
                                             criado_em             TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                             CONSTRAINT uk_leg_emb_ollama_versao UNIQUE (versao_dispositivo_id, modelo, versao_pipeline),
                                             CONSTRAINT ck_leg_emb_ollama_dimensao CHECK (dimensao = 768),
                                             CONSTRAINT ck_leg_emb_ollama_hash CHECK (hash_contextualizado ~ '^[0-9a-f]{64}$')
    );

-- Só vetores ativos entram no índice (versões substituídas saem dele).
-- m e ef_construction são os padrões do pgvector; ajuste só depois do benchmark (scripts/benchmark_embeddings.sql).
CREATE INDEX idx_leg_emb_ollama_hnsw
    ON legislacao_embedding_ollama USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE ativo;

CREATE INDEX idx_leg_emb_ollama_norma ON legislacao_embedding_ollama (norma_id) WHERE ativo;
CREATE INDEX idx_leg_emb_ollama_pendencia ON legislacao_embedding_ollama (modelo, versao_pipeline);