package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.CacheConfig;
import com.exame.ordem.tutor.internal.ProvedorEmbedding;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Único ponto que gera o embedding da pergunta do aluno.
 *
 * <p>Antes, a mesma pergunta era embutida até 3 vezes por mensagem (cache semântico na busca,
 * RAG e cache semântico na gravação). Agora as três etapas passam por aqui: o vetor é calculado
 * uma vez por (provedor, pergunta normalizada) e reaproveitado. {@code Cache.get(key, loader)}
 * também faz single-flight: perguntas iguais simultâneas geram uma só chamada ao provedor.
 */
@Component
class EmbeddingDaPergunta {

    private final Cache cache;
    private final EmbeddingGate gate;

    EmbeddingDaPergunta(CacheManager cacheManager, EmbeddingGate gate) {
        this.cache = cacheManager.getCache(CacheConfig.RAG_EMBEDDING);
        this.gate = gate;
    }

    Embedding obter(ProvedorEmbedding provedor, EmbeddingModel modelo, String pergunta) {
        if (cache == null) {
            return gerar(modelo, pergunta);
        }
        String chave = provedor.name() + ":" + normalizar(pergunta);
        try {
            return cache.get(chave, () -> gerar(modelo, pergunta));
        } catch (Cache.ValueRetrievalException e) {
            if (e.getCause() instanceof RuntimeException causa) {
                throw causa; // preserva o tipo original (rate limit, fila cheia...)
            }
            throw e;
        }
    }

    private Embedding gerar(EmbeddingModel modelo, String pergunta) {
        Embedding gerado = gate.run(() -> modelo.embed(pergunta).content());
        if (gerado.dimension() == 0) {
            throw new IllegalStateException("Provedor de embedding devolveu vetor vazio.");
        }
        return gerado;
    }

    static String normalizar(String s) {
        if (s == null) return "";
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", " ").trim();
    }
}