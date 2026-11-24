package com.exame.ordem.legislacao.internal;

import java.util.UUID;

public record DispositivoPendenteEmbedding(
        UUID versaoId,
        UUID normaId,
        String nomeNorma,
        String tipo,
        String caminho,
        String texto,
        String textoPai
) {}