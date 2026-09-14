package com.exame.ordem.simulados.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ResultadoSimuladoRepository extends JpaRepository<ResultadoSimulado, Long> {
    Optional<ResultadoSimulado> findBySessaoId(Long sessaoId);
}