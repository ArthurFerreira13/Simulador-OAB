-- Módulo: questoes
-- Adiciona colunas de triagem de comentários.
--
-- Contexto: os comentários atuais foram ingeridos com ~30% de erro
-- (blocos deslocados). A triagem por IA classifica cada um como
-- OK / SUSPEITO / ERRADO. Só os ERRADOS são regerados.
--
-- A entidade Alternativa permanece @Immutable — todas as escritas
-- nesta tabela acontecem via SQL direto (agente MCP / migração).
--
-- A V9 criou um trigger de imutabilidade em alternativas. Para escrever
-- durante esta migração é obrigatório o bypass auditado abaixo.

-- 1. Adiciona as colunas de triagem (ALTER TABLE não dispara o trigger).
ALTER TABLE alternativas
    ADD COLUMN comentario_status        VARCHAR(20),
    ADD COLUMN comentario_verificado_em TIMESTAMP,
    ADD COLUMN comentario_motivo        VARCHAR(500),
    ADD COLUMN comentario_modelo        VARCHAR(60);

-- 2. Bypass do trigger da V9 (vale só dentro desta transação).
SET LOCAL app.permitir_alteracao = 'true';

-- 3. Estado inicial: tudo que tem comentário precisa passar pela triagem.
UPDATE alternativas
SET comentario_status = 'NAO_VERIFICADO'
WHERE comentario IS NOT NULL;

-- 4. Restrição de valores válidos para o status.
ALTER TABLE alternativas
    ADD CONSTRAINT ck_alternativas_comentario_status
        CHECK (comentario_status IS NULL
            OR comentario_status IN ('NAO_VERIFICADO','OK','SUSPEITO','ERRADO','CORRIGIDO'));

-- 5. Índice parcial para o agente puxar lotes por status sem varrer tudo.
CREATE INDEX idx_alternativas_comentario_status
    ON alternativas (comentario_status)
    WHERE comentario_status IS NOT NULL;