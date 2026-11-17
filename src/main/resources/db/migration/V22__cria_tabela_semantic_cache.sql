-- V22 — Cache semântico de respostas do tutor.
--
-- Implementa a Camada 2 da arquitetura econômica (Relatório Técnico §5.2):
-- antes de chamar o LLM, busca no pgvector uma pergunta semanticamente
-- equivalente à atual. Se a similaridade de cosseno for >= threshold
-- (default 0.92), reutiliza a resposta revisada; se estiver entre
-- 0.80 e 0.92, o chamador decide (RAG curto ou revisão); abaixo disso,
-- segue o fluxo normal (RAG + LLM).
--
-- Decisões aplicadas:
--   A3 — mesmo EmbeddingModel do RAG (768 dims: Gemini ou Ollama).
--        Misturar espaços vetoriais quebra a busca por similaridade.
--   B1 — cache global (não por questão). A mesma pergunta jurídica
--        ("o que é habeas corpus?") pode ser feita em qualquer questão
--        de Constitucional; isolar por questao_id reduziria o hit rate.
--   C  — threshold 0.92 (conservador, conteúdo jurídico). Configurável
--        em app.tutor.semantic-cache.similarity-threshold.
--   D2 — prompt_version na chave. Se o system prompt do tutor mudar,
--        entradas antigas são ignoradas (não retornam resposta gerada
--        com diretrizes obsoletas).
--
-- Layout: tabela própria (não PgVectorEmbeddingStore do LangChain4j)
-- porque precisamos de colunas analíticas (hits, criado_em, prompt_version)
-- que não cabem bem em metadata JSONB. O embedding é consultado via
-- JdbcTemplate com operador <=> (cosine distance), como o
-- ComentariosPendentesRepository já faz com SQL direto.
--
-- pgvector já habilitado na V3.
-- Dimensão 768: alinhada a comentario_embedding_gemini (V19) e
-- comentario_embedding_ollama (V20).

CREATE TABLE semantic_cache (
                                id                    BIGSERIAL     PRIMARY KEY,
                                pergunta              TEXT          NOT NULL,
                                pergunta_normalizada  TEXT          NOT NULL,
                                resposta              TEXT          NOT NULL,
                                embedding             vector(768)   NOT NULL,
                                prompt_version        VARCHAR(50)   NOT NULL,
                                questao_id            INTEGER       REFERENCES questoes(id),
                                hits                  INTEGER       NOT NULL DEFAULT 0,
                                criado_em             TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                ultimo_acesso_em      TIMESTAMPTZ   NOT NULL DEFAULT now(),

                                CONSTRAINT ck_semantic_cache_hits_nao_negativo
                                    CHECK (hits >= 0),
                                CONSTRAINT ck_semantic_cache_pergunta_nao_vazia
                                    CHECK (length(trim(pergunta)) > 0),
                                CONSTRAINT ck_semantic_cache_resposta_nao_vazia
                                    CHECK (length(trim(resposta)) > 0)
);

-- Busca exata (fallback quando o embedding falha ou está desabilitado).
-- Sem UNIQUE: a mesma pergunta pode ter mais de uma resposta ao longo
-- do tempo (prompt_version diferentes).
CREATE INDEX idx_semantic_cache_pergunta_norm
    ON semantic_cache (pergunta_normalizada);

-- Invalidação por versão do prompt: descarta entradas antigas sem
-- precisar de DROP em massa.
CREATE INDEX idx_semantic_cache_prompt_version
    ON semantic_cache (prompt_version);

-- Ordenação para limpeza por TTL (as mais antigas primeiro).
CREATE INDEX idx_semantic_cache_criado_em
    ON semantic_cache (criado_em);

-- Busca por similaridade de cosseno (HNSW).
-- m=16, ef_construction=64 são os defaults do pgvector e bons para
-- até ~1M vetores com recall alto. Se o cache crescer muito, ef_search
-- pode ser ajustado por sessão em tempo de consulta.
CREATE INDEX idx_semantic_cache_embedding_cosine
    ON semantic_cache
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);