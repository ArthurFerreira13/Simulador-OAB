package com.exame.ordem.legislacao.dto;

import java.nio.file.Path;
import java.util.Set;

/**
 * Parâmetros para consultar https://legis.senado.leg.br/dadosabertos/legislacao/lista.json
 */
public record FiltroSenadoLegislacao(
        String siglaTipoSenado,
        String numero,
        Integer ano,
        Integer pagina,
        String nomeCurtoOverride,
        Path arquivoFallbackLocal,
        boolean indexarNoTutor,
        boolean forcarReprocessamento,
        Set<String> aliases,
        Set<Long> disciplinaIds
) {
    public FiltroSenadoLegislacao {
        aliases = aliases != null ? Set.copyOf(aliases) : Set.of();
        disciplinaIds = disciplinaIds != null ? Set.copyOf(disciplinaIds) : Set.of();
    }

    public static FiltroSenadoLegislacao normaEspecifica(
            String siglaTipoSenado,
            String numero,
            int ano,
            String nomeCurto,
            Path fallbackLocal,
            Set<String> aliases,
            Set<Long> disciplinaIds
    ) {
        return new FiltroSenadoLegislacao(
                siglaTipoSenado, numero, ano, 1, nomeCurto,
                fallbackLocal, true, false, aliases, disciplinaIds
        );
    }

    public static FiltroSenadoLegislacao porPagina(
            String siglaTipoSenado,
            Integer ano,
            int pagina,
            boolean indexarNoTutor,
            Set<Long> disciplinaIds
    ) {
        return new FiltroSenadoLegislacao(
                siglaTipoSenado, null, ano, pagina, null,
                null, indexarNoTutor, false, Set.of(), disciplinaIds
        );
    }
}