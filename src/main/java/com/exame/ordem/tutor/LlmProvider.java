package com.exame.ordem.tutor;

import dev.langchain4j.model.chat.StreamingChatModel;

import java.time.Duration;

public interface LlmProvider {

    StreamingChatModel streaming();

    /** Nome estável do provedor: usado em log, métrica e circuit breaker. */
    default String name() {
        return getClass().getSimpleName();
    }

    /** Prazo máximo até o primeiro fragmento; estourou, a cascata tenta o próximo provedor. */
    default Duration ttftTimeout() {
        return Duration.ofSeconds(12);
    }
}