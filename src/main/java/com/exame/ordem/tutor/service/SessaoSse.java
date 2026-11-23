package com.exame.ordem.tutor.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Dono do emitter: todo envio e encerramento passa pelo lock.
 *
 * <p>Usa {@link ReentrantLock} e não {@code synchronized}: no JDK 21, bloquear em IO dentro de
 * um bloco synchronized prende (pin) a thread de plataforma que carrega a thread virtual. Com
 * muitos clientes lentos isso esgotaria as carriers.
 */
final class SessaoSse {
    private final SseEmitter emitter;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean encerrada = new AtomicBoolean(false);

    SessaoSse(SseEmitter emitter) { this.emitter = emitter; }

    boolean isEncerrada() { return encerrada.get(); }

    void marcarEncerrada() {
        lock.lock();
        try { encerrada.set(true); } finally { lock.unlock(); }
    }

    private void abortarSobLock(Exception e) {
        encerrada.set(true);
        try { emitter.completeWithError(e); } catch (Exception ignored) {}
    }

    void enviarEvento(String nome, Object dados) throws IOException {
        lock.lock();
        try {
            if (encerrada.get()) return;
            try {
                emitter.send(SseEmitter.event().name(nome).data(dados));
            } catch (IOException | RuntimeException e) {
                abortarSobLock(e);
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    void enviarEventoSilencioso(String nome, Object dados) {
        lock.lock();
        try {
            if (encerrada.get()) return;
            try {
                emitter.send(SseEmitter.event().name(nome).data(dados));
            } catch (Exception e) {
                abortarSobLock(e);
            }
        } finally {
            lock.unlock();
        }
    }

    void comEmitter(Consumer<SseEmitter> acao) {
        lock.lock();
        try {
            if (encerrada.get()) return;
            try {
                acao.accept(emitter);
            } catch (RuntimeException e) {
                abortarSobLock(e);
            }
        } finally {
            lock.unlock();
        }
    }

    void concluirComSucesso() {
        lock.lock();
        try {
            if (!encerrada.compareAndSet(false, true)) return;
            try {
                emitter.send(SseEmitter.event().name("completed").data("{}"));
                emitter.complete();
            } catch (Exception e) {
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        } finally {
            lock.unlock();
        }
    }

    void concluirSemEvento() {
        lock.lock();
        try {
            if (!encerrada.compareAndSet(false, true)) return;
            try { emitter.complete(); } catch (Exception ignored) {}
        } finally {
            lock.unlock();
        }
    }

    void completarPorTimeout() {
        lock.lock();
        try {
            encerrada.set(true);
            try { emitter.complete(); } catch (Exception ignored) {}
        } finally {
            lock.unlock();
        }
    }
}
