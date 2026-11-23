package com.exame.ordem.legislacao.dto;

import java.time.Instant;
import java.util.UUID;

public record ArtigoDetalhe(
        UUID artigoId,
        UUID normaId,
        String norma,
        String identificadorArtigo,
        String titulo,
        String texto,
        boolean revogado,
        /** false quando o artigo pertence a uma versão já substituída. */
        boolean versaoVigente,
        String fonteUrl,
        Instant consultadoEm
) {}
