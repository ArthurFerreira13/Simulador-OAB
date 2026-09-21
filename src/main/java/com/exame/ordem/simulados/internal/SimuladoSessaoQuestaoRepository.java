package com.exame.ordem.simulados.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SimuladoSessaoQuestaoRepository extends JpaRepository<SimuladoSessaoQuestao, Long> {
    List<SimuladoSessaoQuestao> findBySessaoIdOrderByOrdemAsc(Long sessaoId);
    boolean existsBySessaoIdAndQuestaoId(Long sessaoId, Integer questaoId);
    Optional<SimuladoSessaoQuestao> findBySessaoIdAndQuestaoId(Long sessaoId, Integer questaoId);

}