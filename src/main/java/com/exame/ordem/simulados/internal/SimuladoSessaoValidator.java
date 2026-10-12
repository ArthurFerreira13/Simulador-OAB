package com.exame.ordem.simulados.internal;

import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import org.springframework.stereotype.Component;

/**
 * Validações comuns de sessão de simulado.
 *
 * <p>Não abandona mais sessões expiradas (D31): apenas sinaliza. Quem
 * finaliza é o próprio fluxo — o cliente é orientado a chamar
 * {@code POST /simulados/{id}/finalizar}, que agora aceita sessão
 * expirada e corrige com as respostas registradas.
 */
@Component
public class SimuladoSessaoValidator {

    private final SimuladoSessaoRepository sessaoRepository;
    private final SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;

    SimuladoSessaoValidator(SimuladoSessaoRepository sessaoRepository,
                            SimuladoSessaoQuestaoRepository sessaoQuestaoRepository) {
        this.sessaoRepository = sessaoRepository;
        this.sessaoQuestaoRepository = sessaoQuestaoRepository;
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
     * Valida que a sessão está em andamento e não expirou.
     * Se expirou, sinaliza com 409 sem abandonar (D31).
     */
    public void validarSessaoEmAndamento(SimuladoSessao sessao) {
        if (sessao.getStatus() != StatusSessao.EM_ANDAMENTO) {
            throw new ConflitoDeEstadoException("Sessão não está em andamento.");
        }
        if (sessao.tempoExpirado()) {
            throw new ConflitoDeEstadoException(
                    "Tempo da sessão expirado. Finalize para ver o resultado.");
        }
    }

    public SimuladoSessaoQuestao buscarVinculo(Long sessaoId, int questaoId) {
        return sessaoQuestaoRepository.findBySessaoIdAndQuestaoId(sessaoId, questaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Questão " + questaoId + " não pertence à sessão " + sessaoId + "."));
    }
}