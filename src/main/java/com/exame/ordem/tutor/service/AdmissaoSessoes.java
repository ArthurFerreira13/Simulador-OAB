package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.TutorIndisponivelException;
import com.exame.ordem.tutor.TutorLimiteAtingidoException;
import com.exame.ordem.tutor.config.TutorProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controle de admissão das sessões SSE do tutor.
 *
 * <ul>
 *   <li>Limite global: acima da capacidade, rejeita rápido (503) em vez de empilhar threads e
 *       conexões de banco.</li>
 *   <li>Limite por usuário: impede que uma conta abra dezenas de streams paralelos e fure a
 *       cota de tokens (que só é somada ao fim de cada resposta).</li>
 * </ul>
 */
@Component
class AdmissaoSessoes {

    private final Semaphore global;
    private final int maxPorUsuario;
    private final ConcurrentHashMap<Long, Integer> porUsuario = new ConcurrentHashMap<>();

    /** Construtor usado pelo Spring. */
    @Autowired
    AdmissaoSessoes(TutorProperties props) {
        this(props.admissao().maxSessoesSimultaneas(), props.admissao().maxStreamsPorUsuario());
    }

    /** Usado diretamente em testes. */
    AdmissaoSessoes(int maxGlobal, int maxPorUsuario) {
        this.global = new Semaphore(maxGlobal);
        this.maxPorUsuario = maxPorUsuario;
    }

    /**
     * @throws TutorIndisponivelException   capacidade global esgotada (503)
     * @throws TutorLimiteAtingidoException usuário já tem streams demais (429)
     */
    Permissao adquirir(Long usuarioId) {
        if (!global.tryAcquire()) {
            throw new TutorIndisponivelException(
                    "Tutor com muitos acessos no momento. Tente novamente em alguns segundos.");
        }
        boolean[] aceito = {false};
        // compute é atômico por chave: checar e incrementar acontecem juntos.
        porUsuario.compute(usuarioId, (id, atual) -> {
            int n = atual == null ? 0 : atual;
            if (n < maxPorUsuario) {
                aceito[0] = true;
                return n + 1;
            }
            return atual;
        });
        if (!aceito[0]) {
            global.release();
            throw new TutorLimiteAtingidoException(
                    "Você já tem " + maxPorUsuario + " resposta(s) em andamento. Aguarde a conclusão.");
        }
        return new Permissao(usuarioId);
    }

    int sessoesAtivas(Long usuarioId) {
        return porUsuario.getOrDefault(usuarioId, 0);
    }

    final class Permissao {
        private final Long usuarioId;
        private final AtomicBoolean liberada = new AtomicBoolean(false);

        private Permissao(Long usuarioId) {
            this.usuarioId = usuarioId;
        }

        /** Idempotente: onCompletion, onError e onTimeout podem chamar em sequência. */
        void liberar() {
            if (liberada.compareAndSet(false, true)) {
                porUsuario.computeIfPresent(usuarioId, (id, n) -> n <= 1 ? null : n - 1);
                global.release();
            }
        }
    }
}