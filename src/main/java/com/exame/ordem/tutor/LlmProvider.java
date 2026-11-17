package com.exame.ordem.tutor;

import dev.langchain4j.model.chat.StreamingChatModel;

public interface LlmProvider {

    StreamingChatModel streaming();

    String name();

    /**
     * TTFT esperado em milissegundos, configurado para este provedor.
     *
     * <p>Usado pelo {@code LlmRouter} para ordenar a cascata. Não usar
     * latência de ping TCP como chave: provedores em localhost (Ollama,
     * FreeLLMAPI) respondem ping em ~2 ms, mas geram o primeiro token
     * em 15–30 s. Ping baixo não significa geração rápida.
     */
    long ttftMs();

    int maxOutputTokens();
}