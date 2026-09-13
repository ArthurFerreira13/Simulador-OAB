// com/exame/ordem/usuarios/dto/CadastroUsuarioRequest.java
package com.exame.ordem.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroUsuarioRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres") String senha,
        @NotBlank @Size(max = 150) String nome
) {}