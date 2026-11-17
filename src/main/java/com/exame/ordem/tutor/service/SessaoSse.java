package com.exame.ordem.tutor.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Dono do emitter: todo envio e encerramento passa pelo lock. */
final class SessaoSse {
    private final SseEmitter emitter;
    private final Object lock = new Object();
    private final AtomicBoolean encerrada = new AtomicBoolean(false);

    SessaoSse(SseEmitter emitter) { this.emitter = emitter; }

    boolean isEncerrada() { return encerrada.get(); }

    void marcarEncerrada() {
        synchronized (lock) { encerrada.set(true); }
    }

    private void abortarSobLock(Exception e) {
        encerrada.set(true);
        try { emitter.completeWithError(e); } catch (Exception ignored) {}
    }

    void enviarEvento(String nome, Object dados) throws IOException {
        synchronized (lock) {
            if (encerrada.get()) return;
            try {
                emitter.send(SseEmitter.event().name(nome).data(dados));
            } catch (IOException | RuntimeException e) {
                abortarSobLock(e);
                throw e;
            }
        }
    }

    void enviarEventoSilencioso(String nome, Object dados) {
        synchronized (lock) {
            if (encerrada.get()) return;
            try {
                emitter.send(SseEmitter.event().name(nome).data(dados));
            } catch (Exception e) {
                abortarSobLock(e);
            }
        }
    }

    void comEmitter(Consumer<SseEmitter> acao) {
        synchronized (lock) {
            if (encerrada.get()) return;
            try {
                acao.accept(emitter);
            } catch (RuntimeException e) {
                abortarSobLock(e);
            }
        }
    }

    void concluirComSucesso() {
        synchronized (lock) {
            if (!encerrada.compareAndSet(false, true)) return;
            try {
                emitter.send(SseEmitter.event().name("completed").data("{}"));
                emitter.complete();
            } catch (Exception e) {
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        }
    }

    void concluirSemEvento() {
        synchronized (lock) {
            if (!encerrada.compareAndSet(false, true)) return;
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    void completarPorTimeout() {
        synchronized (lock) {
            encerrada.set(true);
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }
}