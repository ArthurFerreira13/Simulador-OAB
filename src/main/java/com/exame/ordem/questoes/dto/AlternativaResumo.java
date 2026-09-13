package com.exame.ordem.questoes.dto;

public record AlternativaResumo(
        Long id,
        String letra,
        String texto
        // sem ehCorreta de propósito — gabarito não sai do módulo questoes assim
) {}