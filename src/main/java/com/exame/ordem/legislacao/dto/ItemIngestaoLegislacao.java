package com.exame.ordem.legislacao.dto;

import java.nio.file.Path;
import java.util.Set;

public record ItemIngestaoLegislacao(
        String chaveCache,
        String urlApiExterna,
        Path arquivoFallbackLocal,
        String origem,
        String urlReferencia,
        String formatoEntrada,
        boolean consolidado,
        boolean indexarNoTutor,
        boolean forcarReprocessamento,
        boolean forcarPublicacao,
        DadosNorma dadosNorma,
        Set<String> palavrasChave,
        Set<Long> temas
) {
    public ItemIngestaoLegislacao {
        palavrasChave = palavrasChave != null ? Set.copyOf(palavrasChave) : Set.of();
        temas = temas != null ? Set.copyOf(temas) : Set.of();
    }
}