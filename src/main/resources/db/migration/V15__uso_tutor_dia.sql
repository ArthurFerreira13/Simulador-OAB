-- Módulo: tutor
-- Decisões: D06 (sem FK física para usuarios), D08 (cota diária)
--
-- Cota diária de uso do tutor de IA.
-- Cada linha representa (usuario_id, data). Reset é implícito: a virada
-- do dia gera linha nova. A virada usa America/Sao_Paulo (D30).
--
-- Duas métricas independentes:
--   contador = número de mensagens no dia
--   tokens   = soma de tokens (entrada + saída) no dia
-- O freio real de custo é tokens; contador protege contra frequência.
--
-- usuario_id NÃO tem FK para usuarios. Integridade via evento
-- UsuarioExcluido (D06 — recomendação híbrida).

CREATE TABLE uso_tutor_dia (
                               id            BIGSERIAL   PRIMARY KEY,
                               usuario_id    BIGINT      NOT NULL,
                               data          DATE        NOT NULL,
                               contador      INTEGER     NOT NULL DEFAULT 0,
                               tokens        BIGINT      NOT NULL DEFAULT 0,
                               criado_em     TIMESTAMP   NOT NULL DEFAULT NOW(),
                               atualizado_em TIMESTAMP   NOT NULL DEFAULT NOW(),

                               CONSTRAINT uk_uso_tutor_dia_usuario_data UNIQUE (usuario_id, data),
                               CONSTRAINT ck_uso_tutor_dia_contador_nao_negativo CHECK (contador >= 0),
                               CONSTRAINT ck_uso_tutor_dia_tokens_nao_negativo   CHECK (tokens >= 0)
);