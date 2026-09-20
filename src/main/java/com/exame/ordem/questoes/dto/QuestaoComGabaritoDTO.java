package com.exame.ordem.questoes.dto;

import java.util.List;

public record QuestaoComGabaritoDTO(
        int questaoId,
        int numero,
        String enunciado,
        boolean anulada,
        String disciplina,
        List<AlternativaComGabarito> alternativas
) {}