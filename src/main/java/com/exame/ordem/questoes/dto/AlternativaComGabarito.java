package com.exame.ordem.questoes.dto;

public record AlternativaComGabarito(
        Long id,
        String letra,
        String texto,
        String comentario,
        boolean ehCorreta
) {}