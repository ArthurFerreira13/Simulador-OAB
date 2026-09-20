package com.exame.ordem.questoes.internal;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório SOMENTE LEITURA para Alternativa.
 *
 * Ao estender org.springframework.data.repository.Repository (e não JpaRepository),
 * a API pública NÃO expõe save(), saveAll(), delete(), deleteById(), deleteAll().
 * Qualquer tentativa de escrita não compila.
 */
public interface AlternativaRepository extends Repository<Alternativa, Long> {

    Optional<Alternativa> findById(Long id);

    Optional<Alternativa> findByIdAndQuestaoId(Long id, Integer questaoId);

    List<Alternativa> findByQuestaoId(Integer questaoId);

    boolean existsById(Long id);

    long count();
}