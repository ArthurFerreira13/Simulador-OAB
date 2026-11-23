package com.exame.ordem.tutor.service;

/** A fila de embeddings estourou o tempo de espera. Quem chama degrada (sem RAG / sem cache semântico). */
class EmbeddingIndisponivelException extends RuntimeException {
    EmbeddingIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
