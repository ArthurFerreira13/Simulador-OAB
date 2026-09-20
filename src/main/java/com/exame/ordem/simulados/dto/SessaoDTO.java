package com.exame.ordem.simulados.dto;
import java.time.LocalDateTime;
import java.util.List;

public record SessaoDTO(
        Long sessaoId, String status, int tempoLimiteSegundos, LocalDateTime iniciadoEm,
        List<QuestaoSessaoDTO> questoes
) {}