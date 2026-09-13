package com.exame.ordem.usuarios.dto;

import jakarta.validation.constraints.NotBlank;

public record ExcluirContaRequest(
        @NotBlank String email,
        @NotBlank String senha
) {}