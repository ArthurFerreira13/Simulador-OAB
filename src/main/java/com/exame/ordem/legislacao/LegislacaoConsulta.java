package com.exame.ordem.legislacao;

import com.exame.ordem.legislacao.dto.ContextoConsultaLegislacao;
import com.exame.ordem.legislacao.dto.ResultadoLegislacao;
import com.exame.ordem.legislacao.dto.TrechoNormativo;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta pública de consulta, usada pelo tutor. Só enxerga conteúdo PUBLICADO. Não chama fonte
 * externa nem gera embedding: lê apenas o que a ingestão já publicou.
 */
public interface LegislacaoConsulta {

    /**
     * Recupera trechos citáveis para a pergunta: resolução exata de referência ("art. 5º da CF")
     * e busca textual. O resultado diz se a base é suficiente; sem base suficiente o chamador
     * deve se abster ou avisar a limitação, nunca inventar citação.
     */
    ResultadoLegislacao buscar(ContextoConsultaLegislacao consulta);

    /** Trecho por identificador de versão (para resolver o link de uma citação já emitida). */
    Optional<TrechoNormativo> trecho(UUID versaoDispositivoId);
}
