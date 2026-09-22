-- V11__autovacuum_agressivo.sql
--
-- Configura autovacuum mais agressivo nas tabelas quentes do módulo questoes.
--
-- Motivação (medida via EXPLAIN ANALYZE em 22/09/2026):
--   - 586 Heap Fetches em alternativas (por MVCC acumulado)
--   - Planning Time: 395 ms (por estatísticas velhas)
--   Após VACUUM ANALYZE manual: 765ms → 4ms (190x mais rápido).
--
-- Esta migração versiona as configurações de autovacuum pra que
-- sobrevivam a recriação do banco (rodando Flyway do zero).
--
-- Padrão do Postgres: vacuum em 20% de mudanças, analyze em 10%.
-- Configurado abaixo: vacuum em 5%, analyze em 2% (4x mais frequente).

ALTER TABLE alternativas SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02
    );

ALTER TABLE questoes SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02
    );

ALTER TABLE provas SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02
    );

ALTER TABLE disciplinas SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02
    );