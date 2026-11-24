-- V25 — Fundação do módulo legislacao (Fase A).
--
-- Ordem de numeração: V23 já existe no banco (reserva_tokens_tutor). Esta migration assume
-- que a V24 será a das correções do tutor (índices/cache). Se a ordem de entrega mudar,
-- renumere ANTES de aplicar em qualquer ambiente compartilhado; depois de aplicada, não edite.
--
-- Decisões desta migration (revise antes de aplicar):
--   D1  Schema: public, tabelas com prefixo legislacao_ (convenção atual do banco).
--   D2  Disciplina: tabela associativa, disciplina_id BIGINT SEM FK para disciplinas.
--       O módulo não depende do estado interno de questoes; disciplinas é congelada (V9).
--   D3  Identidade canônica: (esfera, espécie, número, ano), única. URN Lex só quando validada.
--   D4  Vigência: nunca presumida. situacao UNKNOWN é o padrão; datas desconhecidas ficam NULL.
--   D5  Embeddings e índices vetoriais ficam para a Fase B (modelo e dimensão ainda não escolhidos).
--   D6  Estados em CHECK (não ENUM do PostgreSQL), para evoluir sem ALTER TYPE.
--
-- Fora desta migration: remissões e embeddings (Fase B).

-- ---------------------------------------------------------------------------
-- 1. Norma (identidade jurídica)
-- ---------------------------------------------------------------------------
CREATE TABLE legislacao_norma (
                                  id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                  esfera           VARCHAR(20)  NOT NULL DEFAULT 'FEDERAL',
                                  especie          VARCHAR(40)  NOT NULL,
                                  numero           VARCHAR(40),
                                  ano              INTEGER,
                                  chave_canonica   TEXT GENERATED ALWAYS AS (
                                      esfera || ':' || especie || ':' || COALESCE(numero, '') || ':' || COALESCE(ano::text, '')
                                      ) STORED,
                                  urn_lex          VARCHAR(300),
                                  urn_validada     BOOLEAN      NOT NULL DEFAULT FALSE,
                                  epigrafe         VARCHAR(400) NOT NULL,
                                  nome_curto       VARCHAR(160),
                                  ementa           TEXT,
                                  orgao            VARCHAR(200),
                                  data_ato         DATE,
                                  data_publicacao  DATE,
                                  situacao         VARCHAR(30)  NOT NULL DEFAULT 'UNKNOWN',
                                  indexar_no_tutor BOOLEAN      NOT NULL DEFAULT FALSE,
                                  criado_em        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                  atualizado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                  CONSTRAINT uk_leg_norma_chave UNIQUE (chave_canonica),
                                  CONSTRAINT ck_leg_norma_esfera CHECK (esfera IN ('FEDERAL', 'ESTADUAL', 'MUNICIPAL', 'INTERNACIONAL')),
                                  CONSTRAINT ck_leg_norma_especie CHECK (especie IN (
                                                                                     'CONSTITUICAO', 'EMENDA_CONSTITUCIONAL', 'LEI', 'LEI_COMPLEMENTAR', 'LEI_DELEGADA',
                                                                                     'DECRETO', 'DECRETO_LEI', 'MEDIDA_PROVISORIA', 'RESOLUCAO', 'OUTRA')),
                                  CONSTRAINT ck_leg_norma_situacao CHECK (situacao IN (
                                                                                       'UNKNOWN', 'VACATIO_LEGIS', 'VIGENTE', 'PARCIALMENTE_REVOGADA', 'REVOGADA', 'SUSPENSA')),
                                  CONSTRAINT ck_leg_norma_urn CHECK (NOT urn_validada OR urn_lex IS NOT NULL),
                                  CONSTRAINT ck_leg_norma_ano CHECK (ano IS NULL OR ano BETWEEN 1500 AND 2200)
);

CREATE INDEX idx_leg_norma_indexar ON legislacao_norma (indexar_no_tutor) WHERE indexar_no_tutor;

-- Apelidos para resolução exata de referências ("CF", "CC", "Estatuto da OAB").
-- alias_normalizado: minúsculas, sem acento, sem pontuação (mesma normalização da consulta).
CREATE TABLE legislacao_norma_alias (
                                        alias_normalizado VARCHAR(120) PRIMARY KEY,
                                        norma_id          UUID NOT NULL REFERENCES legislacao_norma (id) ON DELETE CASCADE
);

CREATE INDEX idx_leg_alias_norma ON legislacao_norma_alias (norma_id);

-- Disciplinas relacionadas à norma (D2: sem FK entre módulos).
CREATE TABLE legislacao_norma_disciplina (
                                             norma_id      UUID   NOT NULL REFERENCES legislacao_norma (id) ON DELETE CASCADE,
                                             disciplina_id BIGINT NOT NULL,
                                             PRIMARY KEY (norma_id, disciplina_id)
);

CREATE INDEX idx_leg_norma_disc_disciplina ON legislacao_norma_disciplina (disciplina_id);

-- ---------------------------------------------------------------------------
-- 2. Documento-fonte (o que foi coletado, preservado para auditoria e reprocessamento)
-- ---------------------------------------------------------------------------
CREATE TABLE legislacao_documento_fonte (
                                            id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                            norma_id       UUID         NOT NULL REFERENCES legislacao_norma (id),
                                            fonte          VARCHAR(40)  NOT NULL,
                                            url            TEXT,
                                            formato        VARCHAR(20)  NOT NULL,
                                            consolidado    BOOLEAN      NOT NULL DEFAULT FALSE,
                                            checksum       CHAR(64)     NOT NULL,
                                            conteudo_bruto TEXT         NOT NULL,
                                            coletado_em    TIMESTAMPTZ  NOT NULL,
                                            versao_parser  VARCHAR(40),
                                            metadados      JSONB        NOT NULL DEFAULT '{}'::jsonb,
                                            criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                            CONSTRAINT uk_leg_doc_norma_checksum UNIQUE (norma_id, checksum),
                                            CONSTRAINT ck_leg_doc_formato CHECK (formato IN ('HTML', 'TEXTO', 'PDF', 'XML')),
                                            CONSTRAINT ck_leg_doc_checksum CHECK (checksum ~ '^[0-9a-f]{64}$')
    );

CREATE INDEX idx_leg_doc_norma_coleta ON legislacao_documento_fonte (norma_id, coletado_em DESC);

-- ---------------------------------------------------------------------------
-- 3. Job de ingestão (pipeline retomável)
-- ---------------------------------------------------------------------------
CREATE TABLE legislacao_ingestao_job (
                                         id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                         norma_id         UUID         NOT NULL REFERENCES legislacao_norma (id),
                                         documento_id     UUID         REFERENCES legislacao_documento_fonte (id),
                                         estado           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                                         etapa            VARCHAR(40),
                                         tentativas       INTEGER      NOT NULL DEFAULT 0,
                                         erro             TEXT,
                                         versao_pipeline  VARCHAR(40),
                                         iniciado_em      TIMESTAMPTZ,
                                         finalizado_em    TIMESTAMPTZ,
                                         criado_em        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                         atualizado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                         CONSTRAINT ck_leg_job_estado CHECK (estado IN (
                                                                                        'PENDING', 'FETCHED', 'PARSING', 'PARSED', 'EMBEDDING', 'VALIDATING',
                                                                                        'PUBLISHED', 'FAILED', 'SUPERSEDED')),
                                         CONSTRAINT ck_leg_job_tentativas CHECK (tentativas >= 0)
);

-- No máximo um job em andamento por norma (evita ingestões concorrentes da mesma norma).
CREATE UNIQUE INDEX uk_leg_job_em_andamento
    ON legislacao_ingestao_job (norma_id)
    WHERE estado IN ('PENDING', 'FETCHED', 'PARSING', 'PARSED', 'EMBEDDING', 'VALIDATING');

CREATE INDEX idx_leg_job_norma_criado ON legislacao_ingestao_job (norma_id, criado_em DESC);
CREATE INDEX idx_leg_job_estado ON legislacao_ingestao_job (estado);

-- ---------------------------------------------------------------------------
-- 4. Dispositivo (identidade lógica na hierarquia) e suas versões imutáveis
-- ---------------------------------------------------------------------------
CREATE TABLE legislacao_dispositivo (
                                        id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                        norma_id      UUID         NOT NULL REFERENCES legislacao_norma (id),
                                        parent_id     UUID         REFERENCES legislacao_dispositivo (id),
                                        tipo          VARCHAR(20)  NOT NULL,
                                        identificador VARCHAR(80)  NOT NULL,
                                        chave_logica  VARCHAR(400) NOT NULL,
                                        caminho       TEXT         NOT NULL,
                                        ordem         INTEGER      NOT NULL,
                                        criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                        CONSTRAINT uk_leg_disp_norma_chave UNIQUE (norma_id, chave_logica),
                                        CONSTRAINT ck_leg_disp_tipo CHECK (tipo IN (
                                                                                    'PARTE', 'LIVRO', 'TITULO', 'CAPITULO', 'SECAO', 'SUBSECAO',
                                                                                    'ARTIGO', 'PARAGRAFO', 'INCISO', 'ALINEA')),
                                        CONSTRAINT ck_leg_disp_pai CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX idx_leg_disp_norma_ordem ON legislacao_dispositivo (norma_id, ordem);
CREATE INDEX idx_leg_disp_parent ON legislacao_dispositivo (parent_id);

CREATE TABLE legislacao_versao_dispositivo (
                                               id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                               dispositivo_id       UUID         NOT NULL REFERENCES legislacao_dispositivo (id),
                                               versao               INTEGER      NOT NULL,
                                               texto_original       TEXT         NOT NULL,
                                               texto_normalizado    TEXT         NOT NULL,
                                               hash_original        CHAR(64)     NOT NULL,
                                               hash_normalizado     CHAR(64)     NOT NULL,
                                               situacao             VARCHAR(30)  NOT NULL DEFAULT 'UNKNOWN',
                                               tipo_alteracao       VARCHAR(30)  NOT NULL DEFAULT 'NENHUMA',
                                               norma_alteradora_id  UUID         REFERENCES legislacao_norma (id),
                                               norma_alteradora_texto VARCHAR(300),
                                               data_inicio_vigencia DATE,
                                               data_fim_vigencia    DATE,
                                               versao_anterior_id   UUID         REFERENCES legislacao_versao_dispositivo (id),
                                               documento_fonte_id   UUID         NOT NULL REFERENCES legislacao_documento_fonte (id),
                                               job_id               UUID         NOT NULL REFERENCES legislacao_ingestao_job (id),
                                               publicada_em         TIMESTAMPTZ,
                                               atual                BOOLEAN      NOT NULL DEFAULT FALSE,
                                               busca                TSVECTOR GENERATED ALWAYS AS (to_tsvector('portuguese', texto_normalizado)) STORED,
                                               criado_em            TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                               CONSTRAINT uk_leg_versao_disp_numero UNIQUE (dispositivo_id, versao),
                                               CONSTRAINT ck_leg_versao_numero CHECK (versao >= 1),
                                               CONSTRAINT ck_leg_versao_situacao CHECK (situacao IN (
                                                                                                     'UNKNOWN', 'VIGENTE', 'REVOGADO', 'VETADO', 'INCONSTITUCIONAL')),
                                               CONSTRAINT ck_leg_versao_alteracao CHECK (tipo_alteracao IN (
                                                                                                            'NENHUMA', 'REDACAO_DADA', 'INCLUIDO', 'RENUMERADO', 'REVOGADO', 'VETADO')),
                                               CONSTRAINT ck_leg_versao_vigencia CHECK (
                                                   data_fim_vigencia IS NULL OR data_inicio_vigencia IS NULL
                                                       OR data_fim_vigencia >= data_inicio_vigencia),
                                               CONSTRAINT ck_leg_versao_hashes CHECK (
                                                   hash_original ~ '^[0-9a-f]{64}$' AND hash_normalizado ~ '^[0-9a-f]{64}$'),
    -- Só versões publicadas podem ser a versão atual.
    CONSTRAINT ck_leg_versao_atual_publicada CHECK (NOT atual OR publicada_em IS NOT NULL)
);

-- No máximo uma versão atual por dispositivo.
CREATE UNIQUE INDEX uk_leg_versao_atual
    ON legislacao_versao_dispositivo (dispositivo_id) WHERE atual;

CREATE INDEX idx_leg_versao_job ON legislacao_versao_dispositivo (job_id);
CREATE INDEX idx_leg_versao_doc ON legislacao_versao_dispositivo (documento_fonte_id);
CREATE INDEX idx_leg_versao_busca ON legislacao_versao_dispositivo USING gin (busca);

-- Texto jurídico publicado não é reescrito: uma alteração gera NOVA versão.
-- Só os campos de ciclo de vida (publicação, atual, fim de vigência) podem mudar.
CREATE OR REPLACE FUNCTION legislacao_versao_imutavel() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.dispositivo_id        IS DISTINCT FROM OLD.dispositivo_id
    OR NEW.versao                IS DISTINCT FROM OLD.versao
    OR NEW.texto_original        IS DISTINCT FROM OLD.texto_original
    OR NEW.texto_normalizado     IS DISTINCT FROM OLD.texto_normalizado
    OR NEW.hash_original         IS DISTINCT FROM OLD.hash_original
    OR NEW.hash_normalizado      IS DISTINCT FROM OLD.hash_normalizado
    OR NEW.situacao              IS DISTINCT FROM OLD.situacao
    OR NEW.tipo_alteracao        IS DISTINCT FROM OLD.tipo_alteracao
    OR NEW.documento_fonte_id    IS DISTINCT FROM OLD.documento_fonte_id
    OR NEW.job_id                IS DISTINCT FROM OLD.job_id
    OR NEW.versao_anterior_id    IS DISTINCT FROM OLD.versao_anterior_id THEN
        RAISE EXCEPTION 'legislacao_versao_dispositivo % é imutável: crie uma nova versão', OLD.id;
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_legislacao_versao_imutavel
    BEFORE UPDATE ON legislacao_versao_dispositivo
    FOR EACH ROW EXECUTE FUNCTION legislacao_versao_imutavel();