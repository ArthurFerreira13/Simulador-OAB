package com.exame.ordem.questoes.dto;

import java.util.List;

public record RespostaDetalhadaDTO(
        int questaoId,
        Long alternativaEscolhidaId,
        boolean correta,
        List<AlternativaComGabarito> alternativas
) {}