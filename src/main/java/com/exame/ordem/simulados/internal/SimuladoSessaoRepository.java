package com.exame.ordem.simulados.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SimuladoSessaoRepository extends JpaRepository<SimuladoSessao, Long> {
    Optional<SimuladoSessao> findByUsuarioIdAndStatus(Long usuarioId, StatusSessao status);
    Optional<SimuladoSessao> findByIdAndUsuarioId(Long id, Long usuarioId);
}