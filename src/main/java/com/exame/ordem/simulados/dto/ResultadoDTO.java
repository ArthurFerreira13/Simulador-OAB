package com.exame.ordem.simulados.dto;

import java.math.BigDecimal;

public record ResultadoDTO(
        int totalQuestoes, int acertos, int erros, int brancos,
        BigDecimal percentualAcerto, boolean aprovado, int tempoGastoSegundos
) {}