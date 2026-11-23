package com.exame.ordem.legislacao.internal;

import com.exame.ordem.legislacao.dto.ArtigoDetalhe;
import com.exame.ordem.legislacao.dto.TrechoLegislacao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a dados do módulo. Todas as tabelas {@code legislacao_*} pertencem a este módulo.
 * Operações de escrita são curtas e não fazem chamadas externas dentro da transação.
 */
@Repository
public class LegislacaoRepository {

    public static final String PROVEDOR_MANUAL = "MANUAL";

    public static final String VALIDADA = "VALIDADA";
    public static final String PUBLICADA = "PUBLICADA";
    public static final String SUBSTITUIDA = "SUBSTITUIDA";

    public record VersaoInfo(UUID id, UUID normaId, String status) {}

    private final JdbcTemplate jdbc;

    LegislacaoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- escrita ----------

    public UUID upsertNorma(String provedor, String identificadorExterno, String tipo,
                            String numero, Integer ano, String nome, String ementa,
                            Integer disciplinaId, String fonteUrl, Instant agora) {
        return jdbc.queryForObject("""
                INSERT INTO legislacao_norma
                    (id, provedor, identificador_externo, tipo, numero, ano, nome, ementa,
                     disciplina_id, fonte_url, status, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ATIVA', ?, ?)
                ON CONFLICT (provedor, identificador_externo) DO UPDATE
                   SET tipo = EXCLUDED.tipo,
                       numero = EXCLUDED.numero,
                       ano = EXCLUDED.ano,
                       nome = EXCLUDED.nome,
                       ementa = EXCLUDED.ementa,
                       disciplina_id = EXCLUDED.disciplina_id,
                       fonte_url = EXCLUDED.fonte_url,
                       atualizado_em = EXCLUDED.atualizado_em
                RETURNING id
                """,
                UUID.class,
                UUID.randomUUID(), provedor, identificadorExterno, tipo, numero, ano, nome, ementa,
                disciplinaId, fonteUrl, ts(agora), ts(agora));
    }

    public UUID inserirVersao(UUID normaId, String hash, String textoBruto, Instant agora) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO legislacao_versao
                    (id, norma_id, hash_conteudo, texto_bruto, fonte_consultada_em, status, criado_em)
                VALUES (?, ?, ?, ?, ?, 'VALIDADA', ?)
                """,
                id, normaId, hash, textoBruto, ts(agora), ts(agora));
        return id;
    }

    public void inserirArtigos(UUID versaoId, List<TextoLegalParser.ArtigoNormalizado> artigos, Instant agora) {
        OffsetDateTime criadoEm = ts(agora);
        jdbc.batchUpdate("""
                INSERT INTO legislacao_artigo
                    (id, versao_id, identificador, titulo, texto, ordem, revogado, criado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                artigos, 200,
                (ps, a) -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, versaoId);
                    ps.setString(3, a.identificador());
                    ps.setNull(4, Types.VARCHAR);
                    ps.setString(5, a.texto());
                    ps.setInt(6, a.ordem());
                    ps.setBoolean(7, a.revogado());
                    ps.setObject(8, criadoEm);
                });
    }

    /** Substitui a versão publicada (se houver) pela nova, na ordem exigida pelo índice parcial único. */
    public void publicarVersao(UUID normaId, UUID versaoId, LocalDate hoje) {
        jdbc.update("""
                UPDATE legislacao_versao
                   SET status = 'SUBSTITUIDA', vigencia_fim = ?
                 WHERE norma_id = ? AND status = 'PUBLICADA' AND id <> ?
                """, hoje, normaId, versaoId);
        jdbc.update("""
                UPDATE legislacao_versao
                   SET status = 'PUBLICADA', vigencia_inicio = ?, vigencia_fim = NULL
                 WHERE id = ?
                """, hoje, versaoId);
    }

    // ---------- leitura (importação) ----------

    public Optional<VersaoInfo> versaoPorHash(UUID normaId, String hash) {
        return jdbc.query("""
                SELECT id, norma_id, status FROM legislacao_versao
                 WHERE norma_id = ? AND hash_conteudo = ?
                """, (rs, i) -> new VersaoInfo(
                rs.getObject("id", UUID.class),
                rs.getObject("norma_id", UUID.class),
                rs.getString("status")), normaId, hash)
                .stream().findFirst();
    }

    public Optional<VersaoInfo> versao(UUID versaoId) {
        return jdbc.query("""
                SELECT id, norma_id, status FROM legislacao_versao WHERE id = ?
                """, (rs, i) -> new VersaoInfo(
                rs.getObject("id", UUID.class),
                rs.getObject("norma_id", UUID.class),
                rs.getString("status")), versaoId)
                .stream().findFirst();
    }

    public int contarArtigos(UUID versaoId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM legislacao_artigo WHERE versao_id = ?", Integer.class, versaoId);
        return n == null ? 0 : n;
    }

    public int contarArtigosDaVersaoPublicada(UUID normaId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM legislacao_artigo a
                  JOIN legislacao_versao v ON v.id = a.versao_id
                 WHERE v.norma_id = ? AND v.status = 'PUBLICADA'
                """, Integer.class, normaId);
        return n == null ? 0 : n;
    }

    // ---------- leitura (consulta pública) ----------

    public List<TrechoLegislacao> buscarSemantico(float[] consulta, String modelo,
                                                  Integer disciplinaId, int limite) {
        String vetor = toVectorLiteral(consulta);
        StringBuilder sql = new StringBuilder("""
                SELECT a.id AS artigo_id, n.nome AS norma, a.identificador, e.trecho,
                       n.fonte_url, v.fonte_consultada_em,
                       1 - (e.embedding <=> ?::vector) AS relevancia
                  FROM legislacao_trecho_embedding e
                  JOIN legislacao_artigo a ON a.id = e.artigo_id
                  JOIN legislacao_versao v ON v.id = a.versao_id
                  JOIN legislacao_norma n ON n.id = v.norma_id
                 WHERE e.modelo = ?
                   AND v.status = 'PUBLICADA'
                   AND n.status = 'ATIVA'
                   AND a.revogado = FALSE
                """);
        List<Object> args = new ArrayList<>();
        args.add(vetor);
        args.add(modelo);
        if (disciplinaId != null) {
            sql.append(" AND n.disciplina_id = ?\n");
            args.add(disciplinaId);
        }
        sql.append(" ORDER BY e.embedding <=> ?::vector\n LIMIT ?");
        args.add(vetor);
        args.add(limite);

        return jdbc.query(sql.toString(), (rs, i) -> new TrechoLegislacao(
                rs.getObject("artigo_id", UUID.class),
                rs.getString("norma"),
                rs.getString("identificador"),
                rs.getString("trecho"),
                rs.getString("fonte_url"),
                rs.getDouble("relevancia"),
                rs.getObject("fonte_consultada_em", OffsetDateTime.class).toInstant()),
                args.toArray());
    }

    public Optional<ArtigoDetalhe> artigo(UUID artigoId) {
        return jdbc.query("""
                SELECT a.id, n.id AS norma_id, n.nome, a.identificador, a.titulo, a.texto,
                       a.revogado, (v.status = 'PUBLICADA') AS vigente,
                       n.fonte_url, v.fonte_consultada_em
                  FROM legislacao_artigo a
                  JOIN legislacao_versao v ON v.id = a.versao_id
                  JOIN legislacao_norma n ON n.id = v.norma_id
                 WHERE a.id = ? AND v.status IN ('PUBLICADA', 'SUBSTITUIDA')
                """, (rs, i) -> new ArtigoDetalhe(
                rs.getObject("id", UUID.class),
                rs.getObject("norma_id", UUID.class),
                rs.getString("nome"),
                rs.getString("identificador"),
                rs.getString("titulo"),
                rs.getString("texto"),
                rs.getBoolean("revogado"),
                rs.getBoolean("vigente"),
                rs.getString("fonte_url"),
                rs.getObject("fonte_consultada_em", OffsetDateTime.class).toInstant()), artigoId)
                .stream().findFirst();
    }

    // ---------- util ----------

    private static OffsetDateTime ts(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    /** Literal textual do pgvector: [v1,v2,...]. */
    private static String toVectorLiteral(float[] vetor) {
        StringBuilder sb = new StringBuilder(vetor.length * 12);
        sb.append('[');
        for (int i = 0; i < vetor.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vetor[i]);
        }
        return sb.append(']').toString();
    }
}
