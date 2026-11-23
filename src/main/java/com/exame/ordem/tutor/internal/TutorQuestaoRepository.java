package com.exame.ordem.tutor.internal;

import com.exame.ordem.tutor.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Leitura de contexto e alternativas de uma questão.
 *
 * <p><b>Cache com {@code Optional}:</b> desde o Spring Framework 6.1,
 * quando um método {@code @Cacheable} declara retorno {@code Optional<T>},
 * o Spring <em>desembrulha</em> o valor antes de avaliar o {@code unless}:
 * {@code Optional.empty()} vira {@code null}, {@code Optional.of(x)} vira
 * {@code x}. Por isso a expressão correta é apenas {@code #result == null} —
 * {@code #result.isEmpty()} falharia porque o tipo já é {@code QuestaoContexto},
 * não {@code Optional}.
 *
 * <p><b>Política de cache:</b> resultados vazios NÃO são cacheados. Se a
 * questão não existir agora, a próxima chamada vai ao banco em vez de
 * devolver "não existe" velho por até 30 min (TTL de
 * {@code contexto-questao}).
 *
 * <p><b>Chaves:</b> o cache {@code contexto-questao} é compartilhado pelas
 * duas operações; os prefixos {@code "ctx:"} e {@code "alt:"} evitam
 * colisão de tipos.
 *
 * <p><b>{@code alternativasDaQuestao}:</b> retorno {@code List} NÃO é
 * desembrulhado pelo Spring. {@code #result.isEmpty()} continua correto
 * ali — não unificar as expressões dos dois métodos.
 */
@Repository
public class TutorQuestaoRepository {

    private static final RowMapper<QuestaoContexto> CONTEXTO_MAPPER = (rs, i) -> new QuestaoContexto(
            rs.getInt("id"),
            rs.getString("enunciado"),
            rs.getBoolean("anulada"),
            rs.getString("titulo"),
            rs.getInt("ano")
    );

    private static final RowMapper<AlternativaTutorDTO> ALTERNATIVA_MAPPER = (rs, i) -> new AlternativaTutorDTO(
            rs.getLong("id"),
            rs.getString("letra"),
            rs.getString("texto"),
            rs.getBoolean("eh_correta"),
            rs.getString("comentario"),
            rs.getString("comentario_status")
    );

    private final JdbcTemplate jdbc;

    TutorQuestaoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Cacheable(cacheNames = CacheConfig.CONTEXTO_QUESTAO, key = "'ctx:' + #questaoId", sync = true)
    public Optional<QuestaoContexto> contextoDaQuestao(int questaoId) {
        return jdbc.query("""
                SELECT q.id, q.enunciado, q.anulada, p.titulo, p.ano
                  FROM questoes q
                  JOIN provas p ON q.prova_id = p.id
                 WHERE q.id = ?
                """, CONTEXTO_MAPPER, questaoId)
                .stream()
                .findFirst();
    }

    @Cacheable(cacheNames = CacheConfig.CONTEXTO_QUESTAO, key = "'alt:' + #questaoId", sync = true)
    public List<AlternativaTutorDTO> alternativasDaQuestao(int questaoId) {
        return jdbc.query("""
                SELECT id, letra, texto, eh_correta, comentario, comentario_status
                  FROM alternativas
                 WHERE questao_id = ?
                 ORDER BY letra
                """, ALTERNATIVA_MAPPER, questaoId);
    }
}