package com.exame.ordem.questoes.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlternativaRepository extends JpaRepository<Alternativa, Long> {

    Optional<Alternativa> findByIdAndQuestaoId(Long id, Integer questaoId);
}