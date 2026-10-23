package com.exame.ordem.tutor.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitura dos comentários ATUAIS das alternativas, com a triagem de qualidade aplicada.
 *
 * <p><b>Fronteira de módulo:</b> {@code alternativas} pertence ao módulo {@code questoes}. Esta
 * leitura direta por JDBC é a mesma exceção já adotada em {@link ComentariosPendentesRepository}
 * (layout ditado pelo pgvector, sem entidade). Dívida registrada: mover para uma porta do
 * {@code questoes} (ex.: {@code ComentariosQueryService}) quando o módulo expuser essa consulta.
 *
 * <p>Comentários com status bloqueado (padrão: ERRADO e SUSPEITO, da triagem V16) nunca saem
 * daqui. Status {@code null} ou NAO_VERIFICADO passam: enquanto a triagem não termina, bloquear
 * tudo que não é OK esvaziaria o RAG.
 */
@Repository
public class ComentariosRepository {

    private final JdbcTemplate jdbc;

    ComentariosRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<FonteRag> MAPPER_QUESTAO = (rs, i) -> new FonteRag(
            rs.getLong("id"), rs.getInt("questao_id"), rs.getString("letra"),
            rs.getString("comentario"), FonteRag.Origem.QUESTAO, null);

    private static final RowMapper<FonteRag> MAPPER_SEMELHANTE = (rs, i) -> new FonteRag(
            rs.getLong("id"), rs.getInt("questao_id"), rs.getString("letra"),
            rs.getString("comentario"), FonteRag.Origem.SEMELHANTE, null);

    /** Comentários das alternativas da própria questão (consulta exata, sem embedding). */
    public List<FonteRag> daQuestao(int questaoId, Collection<String> statusBloqueados) {
        String sql = """
                SELECT a.id, a.questao_id, a.letra, a.comentario
                  FROM alternativas a
                 WHERE a.questao_id = ?
                   AND a.comentario IS NOT NULL
                   AND TRIM(a.comentario) <> ''
                """ + clausulaStatus(statusBloqueados) + " ORDER BY a.letra";

        List<Object> args = new ArrayList<>();
        args.add(questaoId);
        args.addAll(statusBloqueados);
        return jdbc.query(sql, MAPPER_QUESTAO, args.toArray());
    }

    /** Comentários atuais (e não bloqueados) dos ids pedidos, na ordem de {@code ids}. */
    public Map<Long, FonteRag> atuais(Collection<Long> ids, Collection<String> statusBloqueados) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT a.id, a.questao_id, a.letra, a.comentario
                  FROM alternativas a
                 WHERE a.id = ANY (?)
                   AND a.comentario IS NOT NULL
                   AND TRIM(a.comentario) <> ''
                """ + clausulaStatus(statusBloqueados);

        PreparedStatementSetter setter = ps -> {
            ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids.toArray()));
            int idx = 2;
            for (String status : statusBloqueados) {
                ps.setString(idx++, status);
            }
        };
        List<FonteRag> lidas = jdbc.query(sql, setter, MAPPER_SEMELHANTE);

        Map<Long, FonteRag> porId = new LinkedHashMap<>();
        lidas.forEach(f -> porId.put(f.alternativaId(), f));
        return porId;
    }

    /** Valores sempre por bind (?): só a quantidade de placeholders é montada dinamicamente. */
    private static String clausulaStatus(Collection<String> statusBloqueados) {
        if (statusBloqueados.isEmpty()) {
            return "";
        }
        String marcas = String.join(",", java.util.Collections.nCopies(statusBloqueados.size(), "?"));
        return " AND (a.comentario_status IS NULL OR a.comentario_status NOT IN (" + marcas + "))";
    }
}