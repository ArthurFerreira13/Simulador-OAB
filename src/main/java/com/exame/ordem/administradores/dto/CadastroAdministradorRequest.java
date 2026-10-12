package com.exame.ordem.administradores.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroAdministradorRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String senha,
        @NotBlank String nome,
        @NotBlank String chave
) {}