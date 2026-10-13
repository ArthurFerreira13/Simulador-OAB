-- V14__trial_reverso.sql
--
-- Trial reverso (RF12.8, D11, D32): todo usuário novo ganha 7 dias de
-- acesso completo a partir do cadastro. Depois, cai para GRATUITO (ou PAGO
-- se assinar). O estado é derivado em PoliticaAcessoService, não gravado.
--
-- Backfill: usuários existentes recebem trial retroativo com base em
-- criado_em — todos já estarão expirados, o que é o comportamento correto
-- (o trial nunca foi comunicado a eles).

ALTER TABLE usuarios ADD COLUMN inicio_trial TIMESTAMPTZ;
ALTER TABLE usuarios ADD COLUMN fim_trial    TIMESTAMPTZ;
ALTER TABLE usuarios ADD COLUMN origem_trial VARCHAR(20)
    CHECK (origem_trial IN ('AUTOMATICO', 'ADMIN'));

-- Backfill: quem já existe ganha trial iniciado em criado_em
UPDATE usuarios
SET inicio_trial = criado_em,
    fim_trial    = criado_em + INTERVAL '7 days',
    origem_trial = 'AUTOMATICO'
WHERE inicio_trial IS NULL;

CREATE INDEX idx_usuarios_fim_trial ON usuarios (fim_trial);