CREATE TABLE uso_tutor_dia (
    id                BIGSERIAL PRIMARY KEY,
    usuario_id        BIGINT    NOT NULL,
    data              DATE      NOT NULL,
    contador          INTEGER   NOT NULL DEFAULT 0,
    tokens            BIGINT    NOT NULL DEFAULT 0,
    criado_em         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    tokens_reservados BIGINT    NOT NULL DEFAULT 0
);