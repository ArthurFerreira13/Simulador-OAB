package com.exame.ordem.questoes.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestaoRepository extends JpaRepository<Questao, Integer> {

    @Query("""
        SELECT DISTINCT q FROM Questao q
        JOIN FETCH q.alternativas a
        WHERE q.prova.id = :provaId
        ORDER BY q.numero ASC, a.letra ASC
        """)
    List<Questao> findByProvaIdComAlternativas(@Param("provaId") Integer provaId);

    List<Questao> findByIdIn(List<Integer> ids);
    @Query("""
    SELECT DISTINCT q FROM Questao q
    JOIN FETCH q.alternativas a
    WHERE q.id IN :ids
    ORDER BY q.numero ASC, a.letra ASC
    """)
    List<Questao> findByIdInComAlternativas(@Param("ids") List<Integer> ids);
}