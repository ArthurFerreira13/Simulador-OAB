-- V10__idx_provas_disciplinas.sql
--
-- Índices para acelerar as listagens mais acessadas do módulo questoes.
-- Não afeta o trigger de imutabilidade do V9 (o trigger só cobre UPDATE/DELETE).

-- Ordenação da listagem de provas: ORDER BY ano DESC, id ASC
CREATE INDEX IF NOT EXISTS idx_provas_ano_id
    ON provas (ano DESC, id ASC);

-- Ordenação alfabética da listagem de disciplinas
CREATE INDEX IF NOT EXISTS idx_disciplinas_nome
    ON disciplinas (nome ASC);