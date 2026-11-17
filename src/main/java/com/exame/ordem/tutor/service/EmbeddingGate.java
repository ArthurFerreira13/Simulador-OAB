package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.TutorProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Limita quantas chamadas de embedding rodam ao mesmo tempo. O Ollama local em CPU entrega mais
 * vazão com 1 a 2 chamadas em voo; acima disso a disputa de CPU piora a latência e a vazão.
 * Configurar em app.tutor.embedding.max-concurrency (EMBEDDING_MAX_CONCURRENCY).
 */
@Component
class EmbeddingGate {

    private final Semaphore permits;

    EmbeddingGate(TutorProperties props) {
        this.permits = new Semaphore(props.embedding().maxConcurrency(), true); // true = fila FIFO
    }

    <T> T run(Supplier<T> call) {
        permits.acquireUninterruptibly();
        try {
            return call.get();
        } finally {
            permits.release();
        }
    }
}