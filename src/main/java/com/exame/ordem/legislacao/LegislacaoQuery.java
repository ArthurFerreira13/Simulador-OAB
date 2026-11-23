package com.exame.ordem.legislacao;

import com.exame.ordem.legislacao.dto.ArtigoDetalhe;
import com.exame.ordem.legislacao.dto.TrechoLegislacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta pública do módulo, consumida pelo tutor.
 *
 * <p>Recebe o vetor da pergunta já calculado: o chamador gera o embedding uma única vez
 * e o reaproveita em todas as buscas, sem uma chamada extra ao provedor de embeddings.
 */
public interface LegislacaoQuery {

    /** Dimensão dos vetores armazenados (tabela legislacao_trecho_embedding). */
    int DIMENSAO_EMBEDDING = 768;

    /**
     * Busca semântica restrita a versões PUBLICADAS, normas ATIVAS e artigos não revogados.
     *
     * @param embeddingConsulta vetor da pergunta (deve ter {@link #DIMENSAO_EMBEDDING} posições)
     * @param modeloEmbedding   modelo que gerou o vetor; só são comparados vetores do mesmo modelo
     * @param disciplinaId      filtra por disciplina; {@code null} busca em todas
     * @param limite            entre 1 e 20
     * @param minimoRelevancia  similaridade de cosseno mínima (0 a 1)
     * @return trechos ordenados por relevância; lista vazia se nada superar o mínimo
     */
    List<TrechoLegislacao> buscar(float[] embeddingConsulta,
                                  String modeloEmbedding,
                                  Integer disciplinaId,
                                  int limite,
                                  double minimoRelevancia);

    /** Artigo de uma versão publicada ou substituída (citações antigas continuam resolvendo). */
    Optional<ArtigoDetalhe> artigo(UUID artigoId);
}
