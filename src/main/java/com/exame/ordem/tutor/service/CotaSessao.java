package com.exame.ordem.tutor.service;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estado da cota de UMA sessão do tutor. Resolve a reserva exatamente uma vez: estorno
 * (nada consumido) ou confirmação de tokens (houve resposta).
 *
 * <p>Se a sessão cai depois do primeiro token (timeout, cliente desconectou), cobra uma estimativa
 * do consumo real (entrada estimada + tokens já emitidos), limitada ao máximo de saída, em vez de
 * cobrar sempre o máximo.
 */
final class CotaSessao {

    private final Long usuarioId;
    private final LocalDate data;
    private final TutorCotaSeguro cota;
    private final int tokensMaximosEmFalha;
    private final AtomicBoolean resolvida = new AtomicBoolean(false);
    private final AtomicBoolean primeiroToken = new AtomicBoolean(false);
    private final AtomicInteger tokensEmitidos = new AtomicInteger();
    private volatile int entradaEstimada;

    CotaSessao(Long usuarioId, LocalDate data, TutorCotaSeguro cota, int tokensMaximosEmFalha) {
        this.usuarioId = usuarioId;
        this.data = data;
        this.cota = cota;
        this.tokensMaximosEmFalha = tokensMaximosEmFalha;
    }

    Long usuarioId() { return usuarioId; }

    /** Tokens aproximados do prompt enviado; definido assim que o prompt existe. */
    void definirEntradaEstimada(int tokens) { this.entradaEstimada = Math.max(0, tokens); }

    /** Chamado a cada fragmento recebido do LLM. */
    void registrarToken() {
        primeiroToken.set(true);
        tokensEmitidos.incrementAndGet();
    }

    void confirmarConsumo(int tokensConsumidos) {
        if (resolvida.compareAndSet(false, true)) {
            cota.somarTokens(usuarioId, data, Math.max(0, tokensConsumidos));
        }
    }

    void estornarUmaVez() {
        if (resolvida.compareAndSet(false, true)) cota.estornar(usuarioId, data);
    }

    void encerrarComEstornoSeNecessario() {
        if (!primeiroToken.get()) {
            estornarUmaVez();
        } else {
            confirmarConsumo(Math.min(tokensMaximosEmFalha, entradaEstimada + tokensEmitidos.get()));
        }
    }
}
