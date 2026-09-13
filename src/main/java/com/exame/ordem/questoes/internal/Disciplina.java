package com.exame.ordem.questoes.internal;

import jakarta.persistence.*;

@Entity
@Table(name = "disciplinas")
public class Disciplina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String nome;

    protected Disciplina() {}

    public Long getId() { return id; }
    public String getNome() { return nome; }
}