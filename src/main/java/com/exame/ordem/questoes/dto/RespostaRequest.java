package com.exame.ordem.questoes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RespostaRequest(
        @NotBlank(message = "alternativa é obrigatória")
        @Pattern(regexp = "[A-Da-d]", message = "Alternativa deve ser A, B, C ou D")
        String alternativa
) {
        public RespostaRequest {
                if (alternativa != null) {
                        alternativa = alternativa.trim().toUpperCase();
                }
        }
}