package com.exame.ordem.simulados.dto;

import jakarta.validation.constraints.Min;

public record IniciarSimuladoRequest(
        @Min(value = 1, message = "numeroProva deve ser >= 1")
        int numeroProva
) {}