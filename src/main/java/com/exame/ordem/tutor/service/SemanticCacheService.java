package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.RagProperties;
import com.exame.ordem.tutor.config.SemanticCacheProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * Cache semântico de respostas do tutor, restrito à questão de origem para
 * evitar reutilizar explicações contextuais em questões diferentes.
 *
 * <p><b>Formato do vetor:</b> usado como literal textual
 * ({@code [v1,v2,...]}) com cast {@code ?::vector} no SQL, em vez de
 * {@code PGvector.toSql(...)}. Motivo: o driver PostgreSQL está com
 * escopo {@code runtime} no {@code pom.xml} (padrão do Spring Boot),
 * e {@code PGvector.toSql} exige o driver em tempo de compilação.
 * O literal textual é aceito nativamente pelo pgvector.
 *
 * <p><b>Escolha do embedder:</b> mesmo critério do
 * {@link RagProviderResolver}: Ollama se {@link RagProperties#ollamaHabilitado()},
 * senão Gemini. Isso garante que o vetor da consulta está no mesmo
 * espaço vetorial do vetor armazenado.
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    private final JdbcTemplate jdbc;
    private final SemanticCacheProperties props;
    private final EmbeddingModel embedder;
    private final String provedorNome;
    private final EmbeddingGate gate;

    SemanticCacheService(JdbcTemplate jdbc,
                         SemanticCacheProperties props,
                         RagProperties ragProps,
                         @Qualifier("geminiEmbeddingModel")
                         ObjectProvider<EmbeddingModel> geminiProvider,
                         @Qualifier("ollamaEmbeddingModel")
                         EmbeddingModel ollamaModel,
                         EmbeddingGate gate) {
        this.jdbc = jdbc;
        this.props = props;
        this.gate = gate;

        if (ragProps.ollamaHabilitado()) {
            this.embedder = ollamaModel;
            this.provedorNome = "ollama";
        } else {
            EmbeddingModel gemini = geminiProvider.getIfAvailable();
            if (gemini != null) {
                this.embedder = gemini;
                this.provedorNome = "gemini";
            } else {
                this.embedder = null;
                this.provedorNome = "NENHUM";
            }
        }

        if (props.enabled() && embedder == null) {
            log.warn("SemanticCacheService: enabled=true mas nenhum EmbeddingModel disponível. Cache desabilitado.");
        } else {
            log.info("SemanticCacheService inicializado: enabled={}, provedor={}, threshold={}, promptVersion={}",
                    props.enabled(), provedorNome, props.similarityThreshold(), props.promptVersion());
        }
    }

    public record RespostaCache(String resposta, double similaridade) {}

    public Optional<RespostaCache> buscar(String pergunta, Integer questaoId) {
        if (!props.enabled() || embedder == null || pergunta == null || pergunta.isBlank()) {
            return Optional.empty();
        }

        try {
            Embedding consulta = gate.run(() -> embedder.embed(pergunta).content());
            if (consulta == null) {
                return Optional.empty();
            }
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

            try {
                jdbc.update("""
                        UPDATE semantic_cache
                           SET hits = hits + 1,
                               ultimo_acesso_em = NOW()
                         WHERE id = ?
                        """, linha.id());
            } catch (Exception e) {
                log.debug("Falha ao incrementar hits do cache: {}", e.getMessage());
            }

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
            Embedding emb = gate.run(() -> embedder.embed(pergunta).content());
            if (emb == null) {
                return;
            }
            String vetor = toVectorLiteral(emb.vector());
            String normalizada = normalizar(pergunta);

            jdbc.update("""
                    INSERT INTO semantic_cache
                        (pergunta, pergunta_normalizada, resposta, embedding, prompt_version, questao_id)
                    VALUES (?, ?, ?, ?::vector, ?, ?)
                    """,
                    pergunta, normalizada, resposta, vetor,
                    props.promptVersion(), questaoId);

            log.debug("SemanticCache STORE (questao={}, promptVersion={})",
                    questaoId, props.promptVersion());

        } catch (Exception e) {
            log.warn("Falha no cache semântico (armazenar): {}", e.getMessage());
        }
    }

    /**
     * Converte {@code float[]} no literal textual aceito pelo pgvector:
     * {@code [v1,v2,...]}. Equivalente a {@code PGvector.toSql(...)}, mas
     * sem depender do driver PostgreSQL em tempo de compilação.
     */
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

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", " ")
                .trim();
    }

    private record LinhaCache(long id, String resposta, double similaridade) {}
}