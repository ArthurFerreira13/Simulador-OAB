package com.exame.ordem.administradores.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {
    boolean existsByEmail(String email);
}
