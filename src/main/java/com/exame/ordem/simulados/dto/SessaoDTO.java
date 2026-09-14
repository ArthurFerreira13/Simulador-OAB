package com.exame.ordem.simulados.dto;

import java.util.List;

public record SessaoDTO(
        Long sessaoId, String status, int tempoLimiteSegundos,
        List<QuestaoSessaoDTO> questoes
) {}