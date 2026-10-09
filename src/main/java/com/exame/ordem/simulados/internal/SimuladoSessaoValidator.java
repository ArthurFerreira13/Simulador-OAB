package com.exame.ordem.simulados.internal;

import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import org.springframework.stereotype.Component;

/**
 * Validações comuns de sessão de simulado, usadas por vários services:
 *  - Garante que a sessão pertence ao usuário (evita acesso indevido).
 *  - Garante que a sessão está em andamento (não finalizada/abandonada).
 *  - Reage a sessão expirada (abandona via helper).
 */
@Component
public class SimuladoSessaoValidator {

    private final SimuladoSessaoRepository sessaoRepository;
    private final SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;
    private final SessaoAbandonoHelper sessaoAbandonoHelper;

    SimuladoSessaoValidator(SimuladoSessaoRepository sessaoRepository,
                            SimuladoSessaoQuestaoRepository sessaoQuestaoRepository,
                            SessaoAbandonoHelper sessaoAbandonoHelper) {
        this.sessaoRepository = sessaoRepository;
        this.sessaoQuestaoRepository = sessaoQuestaoRepository;
        this.sessaoAbandonoHelper = sessaoAbandonoHelper;
    }

    /**
     * Busca a sessão garantindo que ela pertence ao usuário.
     * Retorna 404 (não 403) se não for dona — não vaza existência.
     */
    public SimuladoSessao buscarSessaoDoUsuario(Long sessaoId, Long usuarioId) {
        return sessaoRepository.findByIdAndUsuarioId(sessaoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão não encontrada."));
    }

    /**
     * Valida que a sessão está em andamento. Se o tempo expirou,
     * abandona em transação separada e lança ConflitoDeEstado.
     */
    public void validarSessaoEmAndamento(SimuladoSessao sessao) {
        if (sessao.getStatus() != StatusSessao.EM_ANDAMENTO) {
            throw new ConflitoDeEstadoException("Sessão não está em andamento.");
        }
        if (sessao.tempoExpirado()) {
            sessaoAbandonoHelper.abandonarSeExpirada(sessao.getId());
            throw new ConflitoDeEstadoException("Tempo da sessão expirado. Sessão marcada como abandonada.");
        }
    }

    /**
     * Busca o vínculo (sessão x questão) ou lança 404.
     */
    public SimuladoSessaoQuestao buscarVinculo(Long sessaoId, int questaoId) {
        return sessaoQuestaoRepository.findBySessaoIdAndQuestaoId(sessaoId, questaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Questão " + questaoId + " não pertence à sessão " + sessaoId + "."));
    }
}