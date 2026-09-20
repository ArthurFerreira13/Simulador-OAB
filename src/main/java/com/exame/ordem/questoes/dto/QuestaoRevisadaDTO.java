package com.exame.ordem.questoes.dto;

import com.exame.ordem.questoes.dto.QuestaoComGabaritoDTO;

public record QuestaoRevisadaDTO(
        QuestaoComGabaritoDTO questao,
        Long alternativaEscolhidaId,
        boolean correta
) {}