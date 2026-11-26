package com.exame.ordem.legislacao.dto;

import java.util.List;

public record ResumoIngestaoLote(
        int totalProcessadas,
        int sucessos,
        int falhas,
        List<Object> resultados
) {
    public ResumoIngestaoLote {
        resultados = resultados != null ? List.copyOf(resultados) : List.of();
    }
}