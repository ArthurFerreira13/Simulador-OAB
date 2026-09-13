package com.exame.ordem.questoes.internal;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questoes")
public class Questao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prova_id", nullable = false)
    private Prova prova;

    @Column(name = "numero", nullable = false)
    private int numero;

    @Column(name = "enunciado", columnDefinition = "text", nullable = false)
    private String enunciado;

    @Column(name = "anulada", nullable = false)
    private boolean anulada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disciplina_id")
    private Disciplina disciplina;

    @OneToMany(mappedBy = "questao", fetch = FetchType.LAZY)
    @OrderBy("letra ASC")
    private List<Alternativa> alternativas = new ArrayList<>();

    public int getId() {
        return id;
    }

    public Prova getProva() {
        return prova;
    }

    public int getNumero() {
        return numero;
    }

    public String getEnunciado() {
        return enunciado;
    }

    public boolean isAnulada() {
        return anulada;
    }

    public Disciplina getDisciplina() {
        return disciplina;
    }

    public List<Alternativa> getAlternativas() {
        return alternativas;
    }
}