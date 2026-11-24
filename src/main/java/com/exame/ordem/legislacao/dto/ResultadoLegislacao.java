package com.exame.ordem.legislacao.dto;

import java.util.List;

/**
 * @param baseSuficiente false = não há evidência suficiente: o tutor deve se abster ou informar a
 *                       limitação em vez de citar de memória
 * @param aviso          explicação para o chamador (por que não há base; ressalvas dos trechos)
 */
public record ResultadoLegislacao(List<TrechoNormativo> trechos, boolean baseSuficiente, String aviso) {

    public ResultadoLegislacao {
        trechos = trechos == null ? List.of() : List.copyOf(trechos);
    }

    public static ResultadoLegislacao semBase(String aviso) {
        return new ResultadoLegislacao(List.of(), false, aviso);
    }
}
