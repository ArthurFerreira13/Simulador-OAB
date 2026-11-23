package com.exame.ordem.tutor.internal;

import com.exame.ordem.tutor.config.CacheConfig;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class ComentariosRepository {

    private static final RowMapper<FonteRag> MAPPER_QUESTAO = (rs, i) -> new FonteRag(
            rs.getLong("id"), rs.getInt("questao_id"), rs.getString("letra"),
            rs.getString("comentario"), FonteRag.Origem.QUESTAO, null);

    private static final RowMapper<FonteRag> MAPPER_SEMELHANTE = (rs, i) -> new FonteRag(
            rs.getLong("id"), rs.getInt("questao_id"), rs.getString("letra"),
            rs.getString("comentario"), FonteRag.Origem.SEMELHANTE, null);

    private final JdbcTemplate jdbc;
    private final Cache<Object, Object> cacheIndividual;

    ComentariosRepository(JdbcTemplate jdbc, CacheManager cacheManager) {
        this.jdbc = jdbc;
        var springCache = cacheManager.getCache(CacheConfig.COMENTARIO_INDIVIDUAL);
        this.cacheIndividual = (springCache instanceof CaffeineCache cc) ? cc.getNativeCache() : null;
    }

    @Cacheable(
            cacheNames = CacheConfig.COMENTARIOS_QUESTAO,
            key = "#questaoId + ':' + (#statusBloqueados != null ? T(java.util.Set).copyOf(#statusBloqueados) : 'NONE')",
            sync = true
    )
    public List<FonteRag> daQuestao(int questaoId, Collection<String> statusBloqueados) {
        String sql = """
                SELECT a.id, a.questao_id, a.letra, a.comentario
                  FROM alternativas a
                 WHERE a.questao_id = ?
                   AND a.comentario IS NOT NULL
                   AND TRIM(a.comentario) <> ''
                """ + clausulaStatus(statusBloqueados) + " ORDER BY a.letra";

        List<Object> args = new ArrayList<>(1 + (statusBloqueados != null ? statusBloqueados.size() : 0));
        args.add(questaoId);
        if (statusBloqueados != null) {
            args.addAll(statusBloqueados);
        }
        return jdbc.query(sql, MAPPER_QUESTAO, args.toArray());
    }

    @SuppressWarnings("unchecked")
    public Map<Long, FonteRag> atuais(Collection<Long> ids, Collection<String> statusBloqueados) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        Set<String> statusSet = statusBloqueados == null ? Set.of() : Set.copyOf(statusBloqueados);
        Map<Long, FonteRag> resultado = LinkedHashMap.newLinkedHashMap(ids.size());
        List<Long> faltantes = new ArrayList<>();

        if (cacheIndividual != null) {
            for (Long id : ids) {
                Object cached = cacheIndividual.getIfPresent(chaveIndividual(id, statusSet));
                if (cached instanceof Optional<?> opt) {
                    ((Optional<FonteRag>) opt).ifPresent(fonte -> resultado.put(id, fonte));
                } else {
                    faltantes.add(id);
                }
            }
        } else {
            faltantes.addAll(ids);
        }

        if (!faltantes.isEmpty()) {
            Map<Long, FonteRag> doBanco = buscarAtuaisNoBanco(faltantes, statusSet);
            resultado.putAll(doBanco);

            if (cacheIndividual != null) {
                for (Long id : faltantes) {
                    cacheIndividual.put(chaveIndividual(id, statusSet), Optional.ofNullable(doBanco.get(id)));
                }
            }
        }

        return Collections.unmodifiableMap(resultado);
    }

    private Map<Long, FonteRag> buscarAtuaisNoBanco(List<Long> ids, Set<String> statusBloqueados) {
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
        Map<Long, FonteRag> porId = LinkedHashMap.newLinkedHashMap(lidas.size());
        lidas.forEach(f -> porId.put(f.alternativaId(), f));
        return porId;
    }

    private static String chaveIndividual(Long id, Set<String> statusSet) {
        return id + ":" + statusSet;
    }

    private static String clausulaStatus(Collection<String> statusBloqueados) {
        if (statusBloqueados == null || statusBloqueados.isEmpty()) {
            return "";
        }
        String placeholders = String.join(",", Collections.nCopies(statusBloqueados.size(), "?"));
        return " AND (a.comentario_status IS NULL OR a.comentario_status NOT IN (" + placeholders + "))";
    }
}