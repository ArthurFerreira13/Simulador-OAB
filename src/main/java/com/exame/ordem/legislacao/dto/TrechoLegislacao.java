package com.exame.ordem.legislacao.dto;

import java.time.Instant;
import java.util.UUID;

/** Trecho recuperado pela busca semântica, com a fonte necessária para citação. */
public record TrechoLegislacao(
        UUID artigoId,
        String norma,
        String identificadorArtigo,
        String texto,
        String fonteUrl,
        double relevancia,
        Instant consultadoEm
) {}
