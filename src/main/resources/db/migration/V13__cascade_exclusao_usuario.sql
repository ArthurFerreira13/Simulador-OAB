-- V13__cascade_exclusao_usuario.sql
--
-- Habilita exclusão real de conta (RF01.6, LGPD/RNF09).
-- Antes, DELETE FROM usuarios falhava com violação de FK se o usuário
-- tivesse qualquer simulado_sessao.
--
-- Estratégia: CASCADE nos dados individuais (OLTP).
--   - simulado_sessao → CASCADE em usuarios
--   - simulado_sessao_questao → CASCADE em simulado_sessao
--   - resposta_simulado → CASCADE em simulado_sessao
--   - resultado_simulado → CASCADE em simulado_sessao
--
-- resposta_avulsa já tem CASCADE desde a V12.
--
-- Estatísticas agregadas (estatistica_questao, simulado_metricas)
-- NÃO são afetadas — serão criadas em módulo `desempenho` separado,
-- sem usuario_id, alimentadas por eventos (ADR-011, ADR-012).

-- 1. simulado_sessao → CASCADE em usuarios
ALTER TABLE simulado_sessao
DROP CONSTRAINT simulado_sessao_usuario_id_fkey;
ALTER TABLE simulado_sessao
    ADD CONSTRAINT simulado_sessao_usuario_id_fkey
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 2. simulado_sessao_questao → CASCADE em simulado_sessao
ALTER TABLE simulado_sessao_questao
DROP CONSTRAINT simulado_sessao_questao_sessao_id_fkey;
ALTER TABLE simulado_sessao_questao
    ADD CONSTRAINT simulado_sessao_questao_sessao_id_fkey
        FOREIGN KEY (sessao_id) REFERENCES simulado_sessao(id) ON DELETE CASCADE;

-- 3. resposta_simulado → CASCADE em simulado_sessao
ALTER TABLE resposta_simulado
DROP CONSTRAINT resposta_simulado_sessao_id_fkey;
ALTER TABLE resposta_simulado
    ADD CONSTRAINT resposta_simulado_sessao_id_fkey
        FOREIGN KEY (sessao_id) REFERENCES simulado_sessao(id) ON DELETE CASCADE;

-- 4. resultado_simulado → CASCADE em simulado_sessao
ALTER TABLE resultado_simulado
DROP CONSTRAINT resultado_simulado_sessao_id_fkey;
ALTER TABLE resultado_simulado
    ADD CONSTRAINT resultado_simulado_sessao_id_fkey
        FOREIGN KEY (sessao_id) REFERENCES simulado_sessao(id) ON DELETE CASCADE;