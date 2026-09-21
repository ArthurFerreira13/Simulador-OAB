package com.exame.ordem.questoes.internal;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório SOMENTE LEITURA para Disciplina.
 * Sem save()/delete() — a API só expõe consultas.
 */
public interface DisciplinaRepository extends Repository<Disciplina, Long> {

    Optional<Disciplina> findById(Long id);

    List<Disciplina> findAll();

    long count();
}