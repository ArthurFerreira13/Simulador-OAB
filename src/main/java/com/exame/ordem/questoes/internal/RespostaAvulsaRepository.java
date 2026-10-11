package com.exame.ordem.questoes.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de escrita para RespostaAvulsa.
 *
 * Diferente dos outros repositórios do módulo questoes (read-only),
 * este é JpaRepository — precisa de save() e delete().
 */
public interface RespostaAvulsaRepository extends JpaRepository<RespostaAvulsa, Long> {

    Optional<RespostaAvulsa> findByUsuarioIdAndQuestaoId(Long usuarioId, Integer questaoId);

    List<RespostaAvulsa> findByUsuarioIdAndQuestaoIdIn(Long usuarioId, List<Integer> questaoIds);

    long countByUsuarioId(Long usuarioId);

    long countByUsuarioIdAndCorretaTrue(Long usuarioId);

    void deleteByUsuarioId(Long usuarioId);
}