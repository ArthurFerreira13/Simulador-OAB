package com.exame.ordem.simulados.dto;

import com.exame.ordem.questoes.dto.QuestaoRevisadaDTO;

import java.math.BigDecimal;
import java.util.List;

public record ResultadoDTO(
        int totalQuestoes, int acertos, int erros, int brancos,
        BigDecimal percentualAcerto, boolean aprovado, int tempoGastoSegundos,
        List<QuestaoRevisadaDTO> revisao
) {}