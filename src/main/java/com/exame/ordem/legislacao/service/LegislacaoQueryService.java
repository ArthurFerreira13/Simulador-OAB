package com.exame.ordem.legislacao.service;

import com.exame.ordem.legislacao.LegislacaoQuery;
import com.exame.ordem.legislacao.dto.ArtigoDetalhe;
import com.exame.ordem.legislacao.dto.TrechoLegislacao;
import com.exame.ordem.legislacao.internal.LegislacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitura somente local: nunca chama API externa. Se não há conteúdo publicado/indexado,
 * devolve lista vazia e o chamador segue sem fonte legal.
 */
@Service
class LegislacaoQueryService implements LegislacaoQuery {

    private static final Logger log = LoggerFactory.getLogger(LegislacaoQueryService.class);
    private static final int LIMITE_MAXIMO = 20;

    private final LegislacaoRepository repository;

    LegislacaoQueryService(LegislacaoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrechoLegislacao> buscar(float[] embeddingConsulta, String modeloEmbedding,
                                         Integer disciplinaId, int limite, double minimoRelevancia) {
        if (embeddingConsulta == null || embeddingConsulta.length != DIMENSAO_EMBEDDING) {
            throw new IllegalArgumentException("Embedding deve ter " + DIMENSAO_EMBEDDING + " dimensões.");
        }
        if (modeloEmbedding == null || modeloEmbedding.isBlank()) {
            throw new IllegalArgumentException("modeloEmbedding é obrigatório.");
        }
        int limiteEfetivo = Math.max(1, Math.min(limite, LIMITE_MAXIMO));

        long inicio = System.nanoTime();
        List<TrechoLegislacao> encontrados =
                repository.buscarSemantico(embeddingConsulta, modeloEmbedding, disciplinaId, limiteEfetivo);
        List<TrechoLegislacao> aceitos = encontrados.stream()
                .filter(t -> t.relevancia() >= minimoRelevancia)
                .toList();

        log.debug("Busca legislação: {} candidatos, {} aceitos, {} ms",
                encontrados.size(), aceitos.size(), (System.nanoTime() - inicio) / 1_000_000);
        return aceitos;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArtigoDetalhe> artigo(UUID artigoId) {
        return repository.artigo(artigoId);
    }
}
