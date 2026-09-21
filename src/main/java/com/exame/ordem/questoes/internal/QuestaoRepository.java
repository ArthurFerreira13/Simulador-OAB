package com.exame.ordem.questoes.internal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositório SOMENTE LEITURA para Questao.
 * Sem save()/delete() — a API só expõe consultas.
 */
public interface QuestaoRepository extends Repository<Questao, Integer> {

    Optional<Questao> findById(Integer id);

    long count();

    @Query("""
        SELECT DISTINCT q FROM Questao q
        JOIN FETCH q.alternativas a
        LEFT JOIN FETCH q.disciplina
        WHERE q.prova.id = :provaId
        ORDER BY q.numero ASC, a.letra ASC
        """)
    List<Questao> findByProvaIdComAlternativas(@Param("provaId") Integer provaId);

    @Query("""
        SELECT DISTINCT q FROM Questao q
        JOIN FETCH q.alternativas a
        LEFT JOIN FETCH q.disciplina
        WHERE q.id IN :ids
        ORDER BY q.numero ASC, a.letra ASC
        """)
    List<Questao> findByIdInComAlternativas(@Param("ids") List<Integer> ids);

    @Query("""
        SELECT DISTINCT q FROM Questao q
        JOIN FETCH q.alternativas a
        LEFT JOIN FETCH q.disciplina
        WHERE q.disciplina.id = :disciplinaId
        ORDER BY q.numero ASC, a.letra ASC
        """)
    List<Questao> findByDisciplinaIdComAlternativas(@Param("disciplinaId") Long disciplinaId);

    @Query("""
    SELECT DISTINCT q FROM Questao q
    JOIN FETCH q.alternativas a
    LEFT JOIN FETCH q.disciplina d
    LEFT JOIN FETCH q.prova p
    WHERE (:disciplinaId IS NULL OR d.id = :disciplinaId)
      AND (:ano IS NULL OR p.ano = :ano)
    ORDER BY p.ano DESC, p.id ASC, q.numero ASC, a.letra ASC
    """)
    List<Questao> findByDisciplinaEAnoComAlternativas(
            @Param("disciplinaId") Long disciplinaId,
            @Param("ano") Integer ano);
}