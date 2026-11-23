package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.CacheConfig;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
class TutorCacheFacade {
    private final Cache llmCache;
    private final SemanticCacheService semanticCache;

    TutorCacheFacade(CacheManager cacheManager, SemanticCacheService semanticCache) {
        this.llmCache = cacheManager.getCache(CacheConfig.LLM_RESPOSTA);
        this.semanticCache = semanticCache;
    }

    /** Tenta o cache exato e depois o semântico. */
    Optional<String> buscar(String chave, String mensagem, Integer questaoId) {
        String exato = llmCache != null ? llmCache.get(chave, String.class) : null;
        if (exato != null) return Optional.of(exato);
        return semanticCache.buscar(mensagem, questaoId)
                .map(SemanticCacheService.RespostaCache::resposta);
    }

    void armazenar(String chave, String mensagem, String resposta, Integer questaoId) {
        if (resposta.isEmpty()) return;
        // Pergunta com cara de injection: responde ao aluno, mas não planta a resposta para os demais.
        if (PerguntaSuspeita.parece(mensagem)) return;
        if (llmCache != null) llmCache.put(chave, resposta);
        semanticCache.armazenar(mensagem, resposta, questaoId);
    }
}