package com.exame.ordem.simulados.dto;

import jakarta.validation.constraints.NotBlank;

public record CredenciaisRequest(
        @NotBlank String email,
        @NotBlank String senha
) {}