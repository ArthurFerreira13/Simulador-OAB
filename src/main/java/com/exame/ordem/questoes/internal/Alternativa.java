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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entidade somente leitura: representa uma alternativa (A/B/C/D) de uma questão.
 * O campo ehCorreta é exposto via getter/isEhCorreta para permitir a leitura
 * do gabarito.
 */
@Entity
@Table(name = "alternativas")
public class Alternativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questao_id", nullable = false)
    private Questao questao;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "letra", length = 1, nullable = false)
    private String letra; // "A", "B", "C", "D"

    @Column(name = "texto", columnDefinition = "text", nullable = false)
    private String texto;

    @Column(name = "eh_correta", nullable = false)
    private Boolean ehCorreta;

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

    public Boolean getEhCorreta() {
        return ehCorreta;
    }

    public boolean isEhCorreta() {
        return Boolean.TRUE.equals(ehCorreta);
    }
}