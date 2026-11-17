package com.exame.ordem.tutor.service;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

final class CotaSessao {
    private final Long usuarioId;
    private final LocalDate data;
    private final TutorCotaSeguro cota;
    private final int tokensEstimadosEmFalha;
    private final AtomicBoolean resolvida = new AtomicBoolean(false);
    private final AtomicBoolean primeiroToken = new AtomicBoolean(false);

    CotaSessao(Long usuarioId, LocalDate data, TutorCotaSeguro cota, int tokensEstimadosEmFalha) {
        this.usuarioId = usuarioId;
        this.data = data;
        this.cota = cota;
        this.tokensEstimadosEmFalha = tokensEstimadosEmFalha;
    }

    Long usuarioId() { return usuarioId; }

    void registrarPrimeiroToken() { primeiroToken.set(true); }

    void confirmarConsumo(int tokensConsumidos) {
        if (resolvida.compareAndSet(false, true)) {
            cota.somarTokens(usuarioId, data, Math.max(0, tokensConsumidos));
        }
    }

    void estornarUmaVez() {
        if (resolvida.compareAndSet(false, true)) cota.estornar(usuarioId, data);
    }

    void encerrarComEstornoSeNecessario() {
        if (!primeiroToken.get()) estornarUmaVez();
        else confirmarConsumo(tokensEstimadosEmFalha);
    }

}