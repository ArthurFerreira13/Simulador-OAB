-- V24__tutor_indices_cache_e_embeddings.sql
-- Triagem prévia (dev): semantic_cache 50 linhas/0 duplicatas;
-- embeddings gemini e ollama 6080 linhas/6080 alternativas distintas.
-- Por isso não há DELETE de deduplicação.

-- 1) índices redundantes
DROP INDEX IF EXISTS idx_disciplinas_nome;          -- nome já é UNIQUE
DROP INDEX IF EXISTS idx_resposta_avulsa_questao;   -- coberto pela V21 (questao_id, usuario_id)

-- 2) semantic_cache
CREATE INDEX idx_semantic_cache_questao_prompt
    ON semantic_cache (questao_id, prompt_version);

CREATE UNIQUE INDEX uq_semantic_cache_pergunta
    ON semantic_cache (prompt_version, questao_id, pergunta_normalizada)
    NULLS NOT DISTINCT;

-- 3) embeddings: um vetor por alternativa
CREATE UNIQUE INDEX uq_emb_gemini_alt
    ON comentario_embedding_gemini ((metadata->>'alternativaId'));
CREATE UNIQUE INDEX uq_emb_ollama_alt
    ON comentario_embedding_ollama ((metadata->>'alternativaId'));

-- 4) tabelas quentes
ALTER TABLE uso_tutor_dia  SET (fillfactor = 70, autovacuum_vacuum_scale_factor = 0.02);
ALTER TABLE semantic_cache SET (fillfactor = 80, autovacuum_vacuum_scale_factor = 0.02);