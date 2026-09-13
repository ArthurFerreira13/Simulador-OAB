package com.exame.ordem.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtualizarPerfilRequest(
        @NotBlank String email,
        @NotBlank String senhaAtual,
        String novoNome,                              // opcional
        @Size(min = 8) String novaSenha                // opcional
) {}