package com.exame.ordem.usuarios.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AtualizarPerfilRequest(
        String novoNome,

        @Future(message = "A data da prova deve ser uma data futura")
        LocalDate dataProvaAlvo,

        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        String novaSenha
) {}