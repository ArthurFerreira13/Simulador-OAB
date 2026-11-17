package com.exame.ordem.tutor.internal;

public record FonteRag(
        Long alternativaId,
        Integer questaoId,
        String letra,
        String texto,
        Origem origem,
        Double score
) {
    public enum Origem { QUESTAO, SEMELHANTE }

    public FonteRag comScore(Double novoScore) {
        return new FonteRag(alternativaId, questaoId, letra, texto, origem, novoScore);
    }
}