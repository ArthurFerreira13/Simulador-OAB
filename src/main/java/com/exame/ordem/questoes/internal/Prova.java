package com.exame.ordem.questoes.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade somente leitura: representa uma prova do Exame de Ordem.
 * Não possui setters nem construtores públicos — os dados vêm
 * exclusivamente da carga externa (banco/seed).
 */
@Entity
@Table(name = "provas")
public class Prova {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "titulo", length = 150, nullable = false)
    private String titulo;

    @Column(name = "ano", nullable = false)
    private Integer ano;

    @Column(name = "tipo_cor", length = 50)
    private String tipoCor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @OneToMany(mappedBy = "prova", fetch = FetchType.LAZY)
    private List<Questao> questoes = new ArrayList<>();

    public int getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public Integer getAno() {
        return ano;
    }

    public String getTipoCor() {
        return tipoCor;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public List<Questao> getQuestoes() {
        return questoes;
    }
}