package com.exame.ordem.questoes.internal;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidade IMUTÁVEL: representa uma questão do Exame de Ordem.
 * Dados vêm exclusivamente de carga externa (seed/importação).
 */
@Entity
@Immutable
@Table(name = "questoes")
public class Questao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prova_id", nullable = false, updatable = false)
    private Prova prova;

    @Column(name = "numero", nullable = false, updatable = false)
    private int numero;

    @Column(name = "enunciado", columnDefinition = "text", nullable = false, updatable = false)
    private String enunciado;

    @Column(name = "anulada", nullable = false, updatable = false)
    private boolean anulada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disciplina_id", updatable = false)
    private Disciplina disciplina;

    @OneToMany(mappedBy = "questao", fetch = FetchType.LAZY)
    @OrderBy("letra ASC")
    private List<Alternativa> alternativas = new ArrayList<>();

    protected Questao() {}

    public int getId() { return id; }
    public Prova getProva() { return prova; }
    public int getNumero() { return numero; }
    public String getEnunciado() { return enunciado; }
    public boolean isAnulada() { return anulada; }
    public Disciplina getDisciplina() { return disciplina; }
    public List<Alternativa> getAlternativas() { return alternativas; }
}