package com.exame.ordem.questoes.internal;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/**
 * Entidade IMUTÁVEL: representa uma disciplina do Exame de Ordem.
 * Dados vêm exclusivamente de carga externa (seed/importação).
 */
@Entity
@Immutable
@Table(name = "disciplinas")
public class Disciplina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50, updatable = false)
    private String nome;

    protected Disciplina() {}

    public Long getId() { return id; }
    public String getNome() { return nome; }
}