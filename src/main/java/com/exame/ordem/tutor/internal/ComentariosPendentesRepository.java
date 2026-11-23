package com.exame.ordem.tutor.internal;

import com.exame.ordem.tutor.dto.ComentarioPendente;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Lê, em lotes, os comentários que ainda não têm vetor na tabela do provedor.
 *
 * <p>O nome da tabela vai concatenado no SQL: só pode vir do enum fechado {@link ProvedorEmbedding}.
 * O vínculo com a alternativa fica em {@code metadata->>'alternativaId'}; a comparação é textual
 * ({@code = a.id::text}) para poder usar um índice de expressão nessa chave (migration V23).
 */
public class ComentariosPendentesRepository {

    private final JdbcTemplate jdbc;
    private final String tabelaEmbedding;

    public ComentariosPendentesRepository(JdbcTemplate jdbc, ProvedorEmbedding provedor) {
        this.jdbc = jdbc;
        this.tabelaEmbedding = provedor.tabela();
    }

    /**
     * Próximo lote em ordem de id, a partir de (exclusive) {@code aposId}. O cursor garante
     * progresso mesmo quando algum comentário falha ao ser indexado.
     */
    public List<ComentarioPendente> proximoLote(int tamanho, long aposId) {
        if (tamanho <= 0) {
            throw new IllegalArgumentException("Tamanho do lote deve ser positivo.");
        }
        return jdbc.query("""
                SELECT a.id, a.comentario, a.questao_id, a.letra
                  FROM alternativas a
                 WHERE a.id > ?
                   AND a.comentario IS NOT NULL
                   AND TRIM(a.comentario) <> ''
                   AND NOT EXISTS (
                       SELECT 1 FROM %s ce
                        WHERE ce.metadata->>'alternativaId' = a.id::text
                   )
                 ORDER BY a.id
                 LIMIT ?
                """.formatted(tabelaEmbedding),
                (rs, i) -> new ComentarioPendente(
                        rs.getLong("id"),
                        rs.getString("comentario"),
                        rs.getInt("questao_id"),
                        rs.getString("letra")),
                aposId, tamanho);
    }
}
