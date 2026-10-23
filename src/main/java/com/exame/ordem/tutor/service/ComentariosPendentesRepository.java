package com.exame.ordem.tutor.internal;

import com.exame.ordem.tutor.dto.ComentarioPendente;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Único ponto do módulo tutor que conhece o SQL de busca de pendentes.
 *
 * <p>O layout da tabela é ditado pelo PgVectorEmbeddingStore (LangChain4j),
 * por isso o acesso é JDBC e não JPA. Cada instância é amarrada a um
 * {@link ProvedorEmbedding}; como o nome da tabela vai concatenado no SQL
 * (identificador não aceita bind {@code ?}), a defesa é o enum fechado.
 *
 * <p><b>Defesa em profundidade no SQL:</b> o cast para {@code ::bigint}
 * é precedido de filtro regex via {@code CASE}, garantindo que metadata
 * corrompido ou não numérico seja ignorado em vez de derrubar a query.
 *
 * <p><b>Índice (V21):</b> o Postgres só usa um índice funcional quando a expressão da query é
 * idêntica à do índice. Por isso a V21 indexa o {@code CASE} inteiro, não só o
 * {@code (metadata->>'alternativaId')::bigint}; qualquer mudança no CASE abaixo exige mudar a
 * V21 junto (testado: com o índice só no cast, o plano continua em Seq Scan + Sort).
 */
public class ComentariosPendentesRepository {

    private final JdbcTemplate jdbc;
    private final String tabelaEmbedding;

    public ComentariosPendentesRepository(JdbcTemplate jdbc, ProvedorEmbedding provedor) {
        this.jdbc = jdbc;
        this.tabelaEmbedding = provedor.tabela();
    }

    /**
     * Próximas {@code tamanho} alternativas com comentário que ainda não
     * foram indexadas na tabela-alvo.
     *
     * @param tamanho tamanho do lote; deve ser positivo
     * @throws IllegalArgumentException se {@code tamanho <= 0}
     */
    public List<ComentarioPendente> proximoLote(int tamanho) {
        if (tamanho <= 0) {
            throw new IllegalArgumentException("Tamanho do lote deve ser positivo.");
        }
        return jdbc.query("""
                SELECT a.id, a.comentario, a.questao_id, a.letra
                  FROM alternativas a
                 WHERE a.comentario IS NOT NULL
                   AND TRIM(a.comentario) <> ''
                   AND NOT EXISTS (
                       SELECT 1 FROM %s ce
                        WHERE CASE
                                  WHEN ce.metadata->>'alternativaId' ~ '^[0-9]+$'
                                  THEN (ce.metadata->>'alternativaId')::bigint
                                  ELSE NULL
                              END = a.id
                   )
                 ORDER BY a.id
                 LIMIT ?
                """.formatted(tabelaEmbedding),
                (rs, i) -> new ComentarioPendente(
                        rs.getLong("id"),
                        rs.getString("comentario"),
                        rs.getInt("questao_id"),
                        rs.getString("letra")),
                tamanho);
    }
}