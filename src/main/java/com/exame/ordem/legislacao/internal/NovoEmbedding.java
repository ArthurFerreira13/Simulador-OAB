package com.exame.ordem.legislacao.internal;

import java.util.UUID;

public record NovoEmbedding(
        UUID versaoId,
        UUID normaId,
        String modelo,
        String versaoModelo,
        String versaoPipeline,
        String hashContextualizado,
        String textoEmbutido,
        float[] vetor
) {}