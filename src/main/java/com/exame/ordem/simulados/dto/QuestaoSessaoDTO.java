package com.exame.ordem.simulados.dto;

import java.util.List;

public record QuestaoSessaoDTO(
        int questaoId, int numero, String enunciado, boolean anulada,
        List<AlternativaSessaoDTO> alternativas,
        Long alternativaRespondidaId
) {}