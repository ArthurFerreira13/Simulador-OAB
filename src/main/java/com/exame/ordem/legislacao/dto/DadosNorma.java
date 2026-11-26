package com.exame.ordem.legislacao.dto;

import java.time.LocalDate;

public record DadosNorma(
        String esfera,
        String especie,
        String numero,
        Integer ano,
        String epigrafe,
        String nomeCurto,
        String ementa,
        String orgao,
        LocalDate dataAto,
        LocalDate dataPublicacao,
        boolean indexarNoTutor
) {}