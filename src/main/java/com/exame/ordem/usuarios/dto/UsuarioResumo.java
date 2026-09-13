// com/exame/ordem/usuarios/dto/UsuarioResumo.java
package com.exame.ordem.usuarios.dto;

import java.time.LocalDate;

public record UsuarioResumo(Long id, String email, String nome, LocalDate dataProvaAlvo) {}