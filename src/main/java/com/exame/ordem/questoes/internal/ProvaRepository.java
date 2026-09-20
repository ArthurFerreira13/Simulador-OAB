package com.exame.ordem.questoes.internal;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório SOMENTE LEITURA para Prova.
 *
 * Ao estender org.springframework.data.repository.Repository (e não JpaRepository),
 * a API pública NÃO expõe save(), saveAll(), delete(), deleteById(), deleteAll().
 */
public interface ProvaRepository extends Repository<Prova, Integer> {

    Optional<Prova> findById(Integer id);

    List<Prova> findAll();

    long count();

    boolean existsById(Integer id);
}