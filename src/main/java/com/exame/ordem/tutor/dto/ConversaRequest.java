package com.exame.ordem.tutor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConversaRequest(
        @NotNull(message = "questaoId é obrigatório")
        Integer questaoId,

        @NotBlank(message = "mensagem é obrigatória")
        @Size(max = 1000, message = "mensagem deve ter no máximo 1000 caracteres")
        String mensagem
) {}