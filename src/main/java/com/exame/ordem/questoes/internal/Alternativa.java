package com.exame.ordem.questoes.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entidade IMUTÁVEL: representa uma alternativa (A/B/C/D) de uma questão.
 *
 * Regras:
 *  - Nunca pode ser criada, alterada ou removida pela aplicação.
 *  - Todos os dados vêm exclusivamente de carga externa (seed/importação).
 *  - O Hibernate ignora qualquer tentativa de UPDATE (via @Immutable).
 *
 * O campo eh_correta é exposto via isEhCorreta() para permitir a leitura
 * do gabarito sem nunca permitir alteração.
 */
@Entity
@Immutable
@Table(name = "alternativas")
public class Alternativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questao_id", nullable = false, updatable = false)
    private Questao questao;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "letra", length = 1, nullable = false, updatable = false)
    private String letra; // "A", "B", "C", "D"

    @Column(name = "texto", columnDefinition = "text", nullable = false, updatable = false)
    private String texto;

    @Column(name = "comentario", columnDefinition = "TEXT", updatable = false)
    private String comentario;

    @Column(name = "eh_correta", nullable = false, updatable = false)
    private Boolean ehCorreta;

    protected Alternativa() {}

    public Long getId() {
        return id;
    }

    public Questao getQuestao() {
        return questao;
    }

    public String getLetra() {
        return letra;
    }

    public String getTexto() {
        return texto;
    }

    public String getComentario() {
        return comentario;
    }

    public Boolean getEhCorreta() {
        return ehCorreta;
    }

    public boolean isEhCorreta() {
        return Boolean.TRUE.equals(ehCorreta);
    }
}