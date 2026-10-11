package com.exame.ordem.simulados.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResponderQuestaoRequest(
        @NotBlank(message = "alternativa é obrigatória")
        @Pattern(regexp = "[A-Da-d]", message = "Alternativa deve ser A, B, C ou D")
        String alternativa
) {
    public ResponderQuestaoRequest {
        if (alternativa != null) {
            alternativa = alternativa.trim().toUpperCase();
        }
    }
}