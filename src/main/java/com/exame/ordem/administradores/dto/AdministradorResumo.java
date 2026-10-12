package com.exame.ordem.administradores.dto;

import java.time.LocalDateTime;

public record AdministradorResumo(
        Long id,
        String email,
        String nome,
        LocalDateTime criadoEm
) {}