-- V21__idx_performance_alternativas_questoes.sql
--
-- Correção de gargalo de performance identificado via EXPLAIN ANALYZE.
-- O banco estava fazendo Seq Scan (leitura de 6.080 linhas) para buscar
-- alternativas de uma única prova, demorando ~82ms.
--
-- A causa: O PostgreSQL não cria índices automaticamente para FKs.
-- A V1 criou as FKs, mas não os índices.
--
-- Resultado esperado: Tempo de busca cai de ~82ms para < 1ms (Index Scan).

-- 1. Acelera o JOIN de questões com alternativas
CREATE INDEX IF NOT EXISTS idx_alternativas_questao_id
    ON alternativas (questao_id);

-- 2. Acelera o WHERE prova_id = X na busca de questões
CREATE INDEX IF NOT EXISTS idx_questoes_prova_id
    ON questoes (prova_id);

-- 3. Acelera filtros na listagem de respostas avulsas por questão
CREATE INDEX IF NOT EXISTS idx_resposta_avulsa_questao_usuario
    ON resposta_avulsa (questao_id, usuario_id);