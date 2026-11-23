package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.TutorProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Limita a concorrência das chamadas ao provedor de embedding.
 *
 * <p>A espera na fila é limitada ({@code app.tutor.embedding.espera-na-fila}). Estourado o prazo,
 * lança {@link EmbeddingIndisponivelException} e o fluxo do tutor segue sem RAG semântico e sem
 * cache semântico, em vez de empilhar requisições atrás de um provedor lento.
 */
@Component
class EmbeddingGate {

    private final Semaphore permits;
    private final long esperaMs;

    EmbeddingGate(TutorProperties props) {
        this.permits = new Semaphore(props.embedding().maxConcurrency(), true); // FIFO
        this.esperaMs = props.embedding().esperaNaFila().toMillis();
    }

    <T> T run(Supplier<T> call) {
        boolean adquirido;
        try {
            adquirido = permits.tryAcquire(esperaMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingIndisponivelException("Interrompido aguardando vaga de embedding.");
        }
        if (!adquirido) {
            throw new EmbeddingIndisponivelException("Fila de embedding cheia (espera > " + esperaMs + " ms).");
        }
        try {
            return call.get();
        } finally {
            permits.release();
        }
    }
}
