package com.exame.ordem.usuarios.dto;

import jakarta.validation.constraints.Size;

public record AtualizarPerfilRequest(
        String novoNome,
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        String novaSenha
) {}