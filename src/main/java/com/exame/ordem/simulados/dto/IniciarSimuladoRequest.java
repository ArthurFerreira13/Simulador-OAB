package com.exame.ordem.simulados.dto;

import jakarta.validation.constraints.NotBlank;

public record IniciarSimuladoRequest(
        @NotBlank String email,
        @NotBlank String senha,
        int numeroProva
) {}