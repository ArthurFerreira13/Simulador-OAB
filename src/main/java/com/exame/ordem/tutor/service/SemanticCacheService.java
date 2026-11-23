package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.RagProperties;
import com.exame.ordem.tutor.config.SemanticCacheProperties;
import com.exame.ordem.tutor.internal.ProvedorEmbedding;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Cache semântico de respostas do tutor (Camada 2). Antes de chamar o LLM, procura no pgvector
 * uma pergunta equivalente já respondida para a MESMA questão e mesma versão de prompt.
 *
 * <p>Mudanças em relação à versão anterior:
 * <ul>
 *   <li>O vetor da pergunta vem de {@link EmbeddingDaPergunta}: uma chamada ao provedor por
 *       pergunta, compartilhada com o RAG (antes eram até 3).</li>
 *   <li>Gravação sem duplicar: não insere se já existe a mesma pergunta normalizada para a
 *       mesma (prompt_version, questão).</li>
 *   <li>Hits contados em memória e gravados em lote a cada minuto (antes: um UPDATE por hit).</li>
 *   <li>{@code ttlDays} agora vale: limpeza diária de entradas antigas e de versões de prompt
 *       diferentes da atual.</li>
 *   <li>{@link #invalidarQuestao(Integer)} para descartar respostas de uma questão cujo
 *       comentário foi corrigido.</li>
 * </ul>
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    private final JdbcTemplate jdbc;
    private final SemanticCacheProperties props;
    private final EmbeddingModel embedder;
    private final ProvedorEmbedding provedor;
    private final EmbeddingDaPergunta embeddings;
    private final ConcurrentHashMap<Long, LongAdder> hitsPendentes = new ConcurrentHashMap<>();

    SemanticCacheService(JdbcTemplate jdbc,
                         SemanticCacheProperties props,
                         RagProperties ragProps,
                         @Qualifier("geminiEmbeddingModel")
                         ObjectProvider<EmbeddingModel> geminiProvider,
                         @Qualifier("ollamaEmbeddingModel")
                         EmbeddingModel ollamaModel,
                         EmbeddingDaPergunta embeddings) {
        this.jdbc = jdbc;
        this.props = props;
        this.embeddings = embeddings;

        if (ragProps.ollamaHabilitado()) {
            this.embedder = ollamaModel;
            this.provedor = ProvedorEmbedding.OLLAMA;
        } else {
            EmbeddingModel gemini = geminiProvider.getIfAvailable();
            this.embedder = gemini;
            this.provedor = gemini != null ? ProvedorEmbedding.GEMINI : null;
        }

        if (props.enabled() && embedder == null) {
            log.warn("SemanticCacheService: enabled=true mas nenhum EmbeddingModel disponível. Cache desabilitado.");
        } else {
            log.info("SemanticCacheService inicializado: enabled={}, provedor={}, threshold={}, promptVersion={}",
                    props.enabled(), provedor, props.similarityThreshold(), props.promptVersion());
        }
    }

    public record RespostaCache(String resposta, double similaridade) {}

    public Optional<RespostaCache> buscar(String pergunta, Integer questaoId) {
        if (!props.enabled() || embedder == null || pergunta == null || pergunta.isBlank()) {
            return Optional.empty();
        }

        try {
            Embedding consulta = embeddings.obter(provedor, embedder, pergunta);
            String vetor = toVectorLiteral(consulta.vector());

            LinhaCache linha = jdbc.query("""
                    SELECT id,
                           resposta,
                           1 - (embedding <=> ?::vector) AS similaridade
                      FROM semantic_cache
                     WHERE prompt_version = ?
                       AND questao_id = ?
                       AND (1 - (embedding <=> ?::vector)) >= ?
                     ORDER BY embedding <=> ?::vector
                     LIMIT 1
                    """,
                            ps -> {
                                ps.setString(1, vetor);
                                ps.setString(2, props.promptVersion());
                                ps.setObject(3, questaoId);
                                ps.setString(4, vetor);
                                ps.setDouble(5, props.similarityThreshold());
                                ps.setString(6, vetor);
                            },
                            (rs, i) -> new LinhaCache(
                                    rs.getLong("id"),
                                    rs.getString("resposta"),
                                    rs.getDouble("similaridade")))
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (linha == null) {
                return Optional.empty();
            }

            hitsPendentes.computeIfAbsent(linha.id(), id -> new LongAdder()).increment();
            log.debug("SemanticCache HIT (questao={}, score={}, id={})",
                    questaoId, linha.similaridade(), linha.id());
            return Optional.of(new RespostaCache(linha.resposta(), linha.similaridade()));

        } catch (Exception e) {
            log.warn("Falha no cache semântico (buscar): {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void armazenar(String pergunta, String resposta, Integer questaoId) {
        if (!props.enabled() || embedder == null
                || pergunta == null || pergunta.isBlank()
                || resposta == null || resposta.isBlank()) {
            return;
        }

        try {
            Embedding emb = embeddings.obter(provedor, embedder, pergunta);
            String vetor = toVectorLiteral(emb.vector());
            String normalizada = EmbeddingDaPergunta.normalizar(pergunta);

            int inseridos = jdbc.update("""
                    INSERT INTO semantic_cache
                        (pergunta, pergunta_normalizada, resposta, embedding, prompt_version, questao_id)
                    SELECT ?, ?, ?, ?::vector, ?, CAST(? AS INTEGER)
                     WHERE NOT EXISTS (
                           SELECT 1 FROM semantic_cache
                            WHERE prompt_version = ?
                              AND questao_id IS NOT DISTINCT FROM CAST(? AS INTEGER)
                              AND pergunta_normalizada = ?)
                    """,
                    ps -> {
                        ps.setString(1, pergunta);
                        ps.setString(2, normalizada);
                        ps.setString(3, resposta);
                        ps.setString(4, vetor);
                        ps.setString(5, props.promptVersion());
                        ps.setObject(6, questaoId, Types.INTEGER);
                        ps.setString(7, props.promptVersion());
                        ps.setObject(8, questaoId, Types.INTEGER);
                        ps.setString(9, normalizada);
                    });

            log.debug("SemanticCache {} (questao={}, promptVersion={})",
                    inseridos > 0 ? "STORE" : "JA_EXISTE", questaoId, props.promptVersion());

        } catch (Exception e) {
            log.warn("Falha no cache semântico (armazenar): {}", e.getMessage());
        }
    }

    /** Descarta as respostas em cache de uma questão (use ao corrigir um comentário). */
    public int invalidarQuestao(Integer questaoId) {
        int removidas = jdbc.update("DELETE FROM semantic_cache WHERE questao_id = ?", questaoId);
        log.info("SemanticCache: {} entradas removidas da questão {}", removidas, questaoId);
        return removidas;
    }

    /** Grava em lote os hits acumulados em memória. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void descarregarHits() {
        if (hitsPendentes.isEmpty()) {
            return;
        }
        List<Object[]> lote = new ArrayList<>();
        for (Map.Entry<Long, LongAdder> e : hitsPendentes.entrySet()) {
            long n = e.getValue().sumThenReset();
            if (n > 0) {
                lote.add(new Object[]{(int) Math.min(n, Integer.MAX_VALUE), e.getKey()});
            }
        }
        hitsPendentes.values().removeIf(a -> a.sum() == 0);
        if (lote.isEmpty()) {
            return;
        }
        try {
            jdbc.batchUpdate("""
                    UPDATE semantic_cache
                       SET hits = hits + ?, ultimo_acesso_em = NOW()
                     WHERE id = ?
                    """, lote);
        } catch (Exception e) {
            log.debug("Falha ao gravar hits do cache semântico: {}", e.getMessage());
        }
    }

    /** Limpeza diária: entradas além do TTL e de versões de prompt que não são a atual. */
    @Scheduled(cron = "0 30 3 * * *")
    void purgar() {
        try {
            int removidas = jdbc.update("""
                    DELETE FROM semantic_cache
                     WHERE criado_em < NOW() - make_interval(days => ?)
                        OR prompt_version <> ?
                    """, props.ttlDays(), props.promptVersion());
            log.info("SemanticCache purge: {} entradas removidas (ttl={} dias, promptVersion={})",
                    removidas, props.ttlDays(), props.promptVersion());
        } catch (Exception e) {
            log.warn("Falha na limpeza do cache semântico: {}", e.getMessage());
        }
    }

    private static String toVectorLiteral(float[] vetor) {
        StringBuilder sb = new StringBuilder(vetor.length * 12);
        sb.append('[');
        for (int i = 0; i < vetor.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vetor[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private record LinhaCache(long id, String resposta, double similaridade) {}
}
