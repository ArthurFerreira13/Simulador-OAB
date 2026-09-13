package com.exame.ordem.questoes.dto;
import java.util.List;

public record QuestaoResumo(
        int id,
        int provaId,
        int numero,
        String enunciado,
        boolean anulada,
        List<AlternativaResumo> alternativas
) {}