package com.exame.ordem.simulados.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SimuladoSessaoQuestaoRepository extends JpaRepository<SimuladoSessaoQuestao, Long> {
    List<SimuladoSessaoQuestao> findBySessaoIdOrderByOrdemAsc(Long sessaoId);
    boolean existsBySessaoIdAndQuestaoId(Long sessaoId, Integer questaoId);
}