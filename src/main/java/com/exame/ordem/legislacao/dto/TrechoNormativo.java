package com.exame.ordem.legislacao.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Trecho citável. Para citar a fonte exija: {@code fonteUrl}, {@code coletadoEm} e o aviso de
 * conteúdo informativo e não oficial (Portaria 130/2021 da Presidência da República).
 *
 * @param referencia         ex.: "Art. 5º, XI"
 * @param caminhoHierarquico ex.: "Título II > Capítulo I > Art. 5º > XI"
 * @param exato              true quando veio de referência explícita na pergunta
 * @param relevancia         0 a 1 (1 para resolução exata)
 */
public record TrechoNormativo(
        UUID versaoDispositivoId,
        String nomeNorma,
        String referencia,
        String caminhoHierarquico,
        String texto,
        SituacaoVigencia situacao,
        LocalDate dataInicioVigencia,
        LocalDate dataFimVigencia,
        String fonteUrl,
        Instant coletadoEm,
        boolean exato,
        double relevancia
) {}
