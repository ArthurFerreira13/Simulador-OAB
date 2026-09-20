package com.exame.ordem.questoes.internal;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade IMUTÁVEL: representa uma prova do Exame de Ordem.
 * Não possui setters nem construtores públicos — os dados vêm
 * exclusivamente da carga externa (seed/importação).
 */
@Entity
@Immutable
@Table(name = "provas")
public class Prova {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "titulo", length = 150, nullable = false, updatable = false)
    private String titulo;

    @Column(name = "ano", nullable = false, updatable = false)
    private Integer ano;

    @Column(name = "tipo_cor", length = 50, updatable = false)
    private String tipoCor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @OneToMany(mappedBy = "prova", fetch = FetchType.LAZY)
    private List<Questao> questoes = new ArrayList<>();

    protected Prova() {}

    public int getId() { return id; }
    public String getTitulo() { return titulo; }
    public Integer getAno() { return ano; }
    public String getTipoCor() { return tipoCor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public List<Questao> getQuestoes() { return questoes; }
}