package com.exame.ordem.tutor.internal;

/**
 * Comentário usado como fonte da resposta do tutor.
 *
 * <p>{@code texto} é sempre o comentário ATUAL de {@code alternativas} (não o texto gravado no
 * vetor), para não repassar um comentário que já foi corrigido pela triagem.
 *
 * @param origem {@link Origem#QUESTAO} = comentário de uma alternativa da própria questão;
 *               {@link Origem#SEMELHANTE} = achado por similaridade em outra questão
 * @param score  similaridade (0..1) quando veio da busca vetorial; {@code null} para QUESTAO
 */
public record FonteRag(
        Long alternativaId,
        Integer questaoId,
        String letra,
        String texto,
        Origem origem,
        Double score
) {
    public enum Origem { QUESTAO, SEMELHANTE }
}