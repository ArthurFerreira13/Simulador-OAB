package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import com.exame.ordem.tutor.config.TutorProperties;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.tutor.ollama-chat.habilitado", havingValue = "true")
class OllamaLlmProvider implements LlmProvider {

    /**
     * Ollama local: ping ~2 ms, mas inferência com modelo pequeno em
     * hardware doméstico gera o primeiro token em 10–30 s. 20 s é
     * estimativa conservadora. Se precisar de precisão, mover para
     * {@code TutorProperties.OllamaChat.ttft}.
     */
    private static final long TTFT_ESTIMADO_MS = 20_000L;

    private final StreamingChatModel model;
    private final int maxOutputTokens;

    OllamaLlmProvider(@Qualifier("ollamaStreamingChatModel") StreamingChatModel model,
                      TutorProperties properties) {
        this.model = model;
        this.maxOutputTokens = properties.ollamaChat().numPredict();
    }

    @Override public StreamingChatModel streaming() { return model; }
    @Override public String name() { return "ollama"; }
    @Override public long ttftMs() { return TTFT_ESTIMADO_MS; }
    @Override public int maxOutputTokens() { return maxOutputTokens; }
}