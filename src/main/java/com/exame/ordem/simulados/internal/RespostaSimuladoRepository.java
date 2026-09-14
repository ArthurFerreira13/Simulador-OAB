package com.exame.ordem.simulados.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RespostaSimuladoRepository extends JpaRepository<RespostaSimulado, Long> {
    List<RespostaSimulado> findBySessaoId(Long sessaoId);
    Optional<RespostaSimulado> findBySessaoIdAndQuestaoId(Long sessaoId, Integer questaoId);
}