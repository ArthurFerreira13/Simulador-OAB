package com.exame.ordem.legislacao.internal;

import com.exame.ordem.legislacao.dto.DadosNorma;
import com.exame.ordem.legislacao.dto.SituacaoVigencia;
import com.exame.ordem.legislacao.dto.TrechoNormativo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a dados do módulo (JDBC). Todas as tabelas {@code legislacao_*} pertencem a este módulo.
 * Nenhum método faz chamada externa: cada um é uma operação de banco curta.
 */
@Repository
public class LegislacaoRepository {

    public record DocumentoExistente(UUID id, boolean jobPublicado) {}

    public record DispositivoExistente(UUID id, UUID versaoAtualId, String hashAtual, String situacaoAtual,
                                       int ultimaVersao, int ordem, String caminho, UUID parentId) {}

    private final JdbcTemplate jdbc;

    LegislacaoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ normas

    public UUID upsertNorma(DadosNorma n) {
        return jdbc.queryForObject("""
                INSERT INTO legislacao_norma
                    (esfera, especie, numero, ano, epigrafe, nome_curto, ementa, orgao,
                     data_ato, data_publicacao, indexar_no_tutor)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (chave_canonica) DO UPDATE
                   SET epigrafe = EXCLUDED.epigrafe,
                       nome_curto = EXCLUDED.nome_curto,
                       ementa = EXCLUDED.ementa,
                       orgao = EXCLUDED.orgao,
                       data_ato = EXCLUDED.data_ato,
                       data_publicacao = EXCLUDED.data_publicacao,
                       indexar_no_tutor = EXCLUDED.indexar_no_tutor,
                       atualizado_em = now()
                RETURNING id
                """, UUID.class,
                n.esfera(), n.especie(), n.numero(), n.ano(), n.epigrafe(), n.nomeCurto(), n.ementa(),
                n.orgao(), n.dataAto(), n.dataPublicacao(), n.indexarNoTutor());
    }

    public void substituirAliases(UUID normaId, Collection<String> aliasNormalizados) {
        jdbc.update("DELETE FROM legislacao_norma_alias WHERE norma_id = ?", normaId);
        for (String alias : aliasNormalizados) {
            // Alias é globalmente único: se já pertence a outra norma, o primeiro dono permanece.
            jdbc.update("""
                    INSERT INTO legislacao_norma_alias (alias_normalizado, norma_id) VALUES (?, ?)
                    ON CONFLICT (alias_normalizado) DO NOTHING
                    """, alias, normaId);
        }
    }

    public void substituirDisciplinas(UUID normaId, Collection<Long> disciplinaIds) {
        jdbc.update("DELETE FROM legislacao_norma_disciplina WHERE norma_id = ?", normaId);
        for (Long id : disciplinaIds) {
            jdbc.update("""
                    INSERT INTO legislacao_norma_disciplina (norma_id, disciplina_id) VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, normaId, id);
        }
    }

    // ------------------------------------------------------------------ documento e job

    /** Documento já coletado com o mesmo checksum, e se algum job dele chegou a PUBLISHED/SUPERSEDED. */
    public Optional<DocumentoExistente> documentoPorChecksum(UUID normaId, String checksum) {
        return jdbc.query("""
                SELECT f.id,
                       EXISTS (SELECT 1 FROM legislacao_ingestao_job j
                                WHERE j.documento_id = f.id AND j.estado IN ('PUBLISHED', 'SUPERSEDED')) AS publicado
                  FROM legislacao_documento_fonte f
                 WHERE f.norma_id = ? AND f.checksum = ?
                """, (rs, i) -> new DocumentoExistente(rs.getObject("id", UUID.class), rs.getBoolean("publicado")),
                normaId, checksum).stream().findFirst();
    }

    public UUID inserirDocumento(UUID normaId, String fonte, String url, String formato, boolean consolidado,
                                 String checksum, String conteudoBruto, Instant coletadoEm, String versaoParser) {
        return jdbc.queryForObject("""
                INSERT INTO legislacao_documento_fonte
                    (norma_id, fonte, url, formato, consolidado, checksum, conteudo_bruto, coletado_em, versao_parser)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class,
                normaId, fonte, url, formato, consolidado, checksum, conteudoBruto, ts(coletadoEm), versaoParser);
    }

    /** Falha com DuplicateKeyException se já houver job em andamento para a norma. */
    public UUID criarJob(UUID normaId, UUID documentoId, String versaoPipeline) {
        return jdbc.queryForObject("""
                INSERT INTO legislacao_ingestao_job
                    (norma_id, documento_id, estado, etapa, versao_pipeline, iniciado_em, tentativas)
                VALUES (?, ?, 'PARSING', 'PARSING', ?, now(), 1)
                RETURNING id
                """, UUID.class, normaId, documentoId, versaoPipeline);
    }

    public void atualizarJob(UUID jobId, String estado, String etapa, String erro) {
        jdbc.update("""
                UPDATE legislacao_ingestao_job
                   SET estado = ?, etapa = ?, erro = ?, atualizado_em = now(),
                       finalizado_em = CASE WHEN ? IN ('PUBLISHED', 'FAILED', 'SUPERSEDED') THEN now() ELSE finalizado_em END
                 WHERE id = ?
                """, estado, etapa, erro, estado, jobId);
    }

    public boolean consolidado(UUID documentoId) {
        Boolean c = jdbc.queryForObject(
                "SELECT consolidado FROM legislacao_documento_fonte WHERE id = ?", Boolean.class, documentoId);
        return Boolean.TRUE.equals(c);
    }

    // ------------------------------------------------------------------ dispositivos e versões

    public int contarArtigosAtuais(UUID normaId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*)
                  FROM legislacao_dispositivo d
                  JOIN legislacao_versao_dispositivo v ON v.dispositivo_id = d.id AND v.atual
                 WHERE d.norma_id = ? AND d.tipo = 'ARTIGO'
                """, Integer.class, normaId);
        return n == null ? 0 : n;
    }

    /** Mapa chave lógica -> dispositivo existente, com a versão atual (se houver). */
    public Map<String, DispositivoExistente> dispositivosDaNorma(UUID normaId) {
        Map<String, DispositivoExistente> mapa = new HashMap<>();
        jdbc.query("""
                SELECT d.chave_logica, d.id, d.ordem, d.caminho, d.parent_id,
                       cur.id AS versao_atual_id, cur.hash_normalizado AS hash_atual, cur.situacao AS situacao_atual,
                       COALESCE((SELECT max(x.versao) FROM legislacao_versao_dispositivo x
                                  WHERE x.dispositivo_id = d.id), 0) AS ultima_versao
                  FROM legislacao_dispositivo d
                  LEFT JOIN legislacao_versao_dispositivo cur ON cur.dispositivo_id = d.id AND cur.atual
                 WHERE d.norma_id = ?
                """, rs -> {
            mapa.put(rs.getString("chave_logica"), new DispositivoExistente(
                    rs.getObject("id", UUID.class),
                    rs.getObject("versao_atual_id", UUID.class),
                    rs.getString("hash_atual"),
                    rs.getString("situacao_atual"),
                    rs.getInt("ultima_versao"),
                    rs.getInt("ordem"),
                    rs.getString("caminho"),
                    rs.getObject("parent_id", UUID.class)));
        }, normaId);
        return mapa;
    }

    public UUID inserirDispositivo(UUID normaId, UUID parentId, String tipo, String identificador,
                                   String chaveLogica, String caminho, int ordem) {
        return jdbc.queryForObject("""
                INSERT INTO legislacao_dispositivo
                    (norma_id, parent_id, tipo, identificador, chave_logica, caminho, ordem)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, normaId, parentId, tipo, identificador, chaveLogica, caminho, ordem);
    }

    public void atualizarPosicao(UUID dispositivoId, UUID parentId, String caminho, int ordem) {
        jdbc.update("UPDATE legislacao_dispositivo SET parent_id = ?, caminho = ?, ordem = ? WHERE id = ?",
                parentId, caminho, ordem, dispositivoId);
    }

    public record NovaVersao(UUID dispositivoId, int versao, String textoOriginal, String textoNormalizado,
                             String hashOriginal, String hashNormalizado, String situacao, String tipoAlteracao,
                             String normaAlteradoraTexto, UUID versaoAnteriorId, UUID documentoId, UUID jobId) {}

    public void inserirVersoes(List<NovaVersao> versoes) {
        jdbc.batchUpdate("""
                INSERT INTO legislacao_versao_dispositivo
                    (dispositivo_id, versao, texto_original, texto_normalizado, hash_original, hash_normalizado,
                     situacao, tipo_alteracao, norma_alteradora_texto, versao_anterior_id, documento_fonte_id, job_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, versoes, 200, (ps, v) -> {
            ps.setObject(1, v.dispositivoId());
            ps.setInt(2, v.versao());
            ps.setString(3, v.textoOriginal());
            ps.setString(4, v.textoNormalizado());
            ps.setString(5, v.hashOriginal());
            ps.setString(6, v.hashNormalizado());
            ps.setString(7, v.situacao());
            ps.setString(8, v.tipoAlteracao());
            if (v.normaAlteradoraTexto() == null) ps.setNull(9, Types.VARCHAR); else ps.setString(9, v.normaAlteradoraTexto());
            ps.setObject(10, v.versaoAnteriorId(), Types.OTHER);
            ps.setObject(11, v.documentoId());
            ps.setObject(12, v.jobId());
        });
    }

    /**
     * Publica, em uma única transação do chamador, todas as versões do job: rebaixa as versões atuais
     * substituídas e promove as novas. Datas de vigência não são presumidas (ficam como estão).
     */
    public int publicarJob(UUID normaId, UUID jobId) {
        jdbc.update("""
                UPDATE legislacao_versao_dispositivo v SET atual = false
                  FROM legislacao_versao_dispositivo n
                 WHERE n.job_id = ? AND n.dispositivo_id = v.dispositivo_id AND v.atual AND v.id <> n.id
                """, jobId);
        int promovidas = jdbc.update("""
                UPDATE legislacao_versao_dispositivo SET publicada_em = now(), atual = true
                 WHERE job_id = ? AND publicada_em IS NULL
                """, jobId);
        jdbc.update("""
                UPDATE legislacao_ingestao_job SET estado = 'SUPERSEDED', atualizado_em = now()
                 WHERE norma_id = ? AND estado = 'PUBLISHED' AND id <> ?
                """, normaId, jobId);
        return promovidas;
    }

    // ------------------------------------------------------------------ consulta

    private static final String SELECT_TRECHO = """
            SELECT v.id AS versao_id, COALESCE(n.nome_curto, n.epigrafe) AS norma, d.caminho, d.ordem,
                   v.texto_original, v.situacao, v.data_inicio_vigencia, v.data_fim_vigencia,
                   f.url, f.coletado_em
              FROM legislacao_dispositivo d
              JOIN legislacao_versao_dispositivo v ON v.dispositivo_id = d.id AND v.atual
              JOIN legislacao_documento_fonte f ON f.id = v.documento_fonte_id
              JOIN legislacao_norma n ON n.id = d.norma_id
            """;

    private RowMapper<TrechoNormativo> mapper(boolean exato, double relevanciaFixa) {
        return (rs, i) -> trecho(rs, exato, relevanciaFixa >= 0 ? relevanciaFixa : rs.getDouble("relevancia"));
    }

    private static TrechoNormativo trecho(ResultSet rs, boolean exato, double relevancia) throws SQLException {
        String caminho = rs.getString("caminho");
        Date inicio = rs.getDate("data_inicio_vigencia");
        Date fim = rs.getDate("data_fim_vigencia");
        return new TrechoNormativo(
                rs.getObject("versao_id", UUID.class),
                rs.getString("norma"),
                referenciaDe(caminho),
                caminho,
                rs.getString("texto_original"),
                SituacaoVigencia.valueOf(rs.getString("situacao")),
                inicio == null ? null : inicio.toLocalDate(),
                fim == null ? null : fim.toLocalDate(),
                rs.getString("url"),
                rs.getObject("coletado_em", OffsetDateTime.class).toInstant(),
                exato,
                relevancia);
    }

    /** "Título II > Capítulo I > Art. 5º > XI" vira "Art. 5º, XI". */
    static String referenciaDe(String caminho) {
        String[] partes = caminho.split(" > ");
        for (int i = 0; i < partes.length; i++) {
            if (partes[i].startsWith("Art. ")) {
                return String.join(", ", java.util.Arrays.copyOfRange(partes, i, partes.length));
            }
        }
        return partes[partes.length - 1];
    }

    public Optional<UUID> normaPorAlias(String aliasNormalizado) {
        return jdbc.query("SELECT norma_id FROM legislacao_norma_alias WHERE alias_normalizado = ?",
                (rs, i) -> rs.getObject("norma_id", UUID.class), aliasNormalizado).stream().findFirst();
    }

    public List<String> todosAliases() {
        return jdbc.queryForList("SELECT alias_normalizado FROM legislacao_norma_alias", String.class);
    }

    /** Artigo e todos os seus descendentes (parágrafos, incisos, alíneas), na ordem do texto. */
    public List<TrechoNormativo> artigoComFilhos(UUID normaId, String chaveArtigo, int limite) {
        return jdbc.query(SELECT_TRECHO + """
                 WHERE d.norma_id = ? AND (d.chave_logica = ? OR d.chave_logica LIKE ?)
                 ORDER BY d.ordem
                 LIMIT ?
                """, mapper(true, 1.0), normaId, chaveArtigo, chaveArtigo + "/%", limite);
    }

    /**
     * Busca textual com OR entre os lexemas da pergunta (a pergunta é linguagem natural; AND
     * zeraria os resultados). Só normas indexadas no tutor e não revogadas; só dispositivos de
     * conteúdo; rank normalizado (0 a 1).
     */
    public List<TrechoNormativo> buscaTextual(String pergunta, Long disciplinaId, UUID normaId, int limite,
                                              double minimoRank) {
        StringBuilder sql = new StringBuilder("""
                WITH q AS (
                    SELECT to_tsquery('simple', string_agg(quote_literal(lexeme), ' | ')) AS tsq
                      FROM unnest(to_tsvector('portuguese', ?))
                )
                SELECT v.id AS versao_id, COALESCE(n.nome_curto, n.epigrafe) AS norma, d.caminho, d.ordem,
                       v.texto_original, v.situacao, v.data_inicio_vigencia, v.data_fim_vigencia,
                       f.url, f.coletado_em, ts_rank_cd(v.busca, q.tsq, 32) AS relevancia
                  FROM legislacao_versao_dispositivo v
                  JOIN legislacao_dispositivo d ON d.id = v.dispositivo_id
                  JOIN legislacao_norma n ON n.id = d.norma_id
                  JOIN legislacao_documento_fonte f ON f.id = v.documento_fonte_id, q
                 WHERE v.atual
                   AND n.indexar_no_tutor
                   AND n.situacao <> 'REVOGADA'
                   AND d.tipo IN ('ARTIGO', 'PARAGRAFO', 'INCISO', 'ALINEA')
                   AND v.busca @@ q.tsq
                   AND ts_rank_cd(v.busca, q.tsq, 32) >= ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(pergunta);
        args.add(minimoRank);
        if (normaId != null) {
            sql.append(" AND n.id = ?\n");
            args.add(normaId);
        }
        if (disciplinaId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM legislacao_norma_disciplina nd"
                    + " WHERE nd.norma_id = n.id AND nd.disciplina_id = ?)\n");
            args.add(disciplinaId);
        }
        sql.append(" ORDER BY relevancia DESC, d.ordem\n LIMIT ?");
        args.add(limite);
        return jdbc.query(sql.toString(), mapper(false, -1), args.toArray());
    }

    public Optional<TrechoNormativo> trechoPorVersao(UUID versaoId) {
        return jdbc.query(SELECT_TRECHO.replace("AND v.atual", "AND v.publicada_em IS NOT NULL")
                        + " WHERE v.id = ?", mapper(true, 1.0), versaoId)
                .stream().findFirst();
    }

    private static OffsetDateTime ts(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}