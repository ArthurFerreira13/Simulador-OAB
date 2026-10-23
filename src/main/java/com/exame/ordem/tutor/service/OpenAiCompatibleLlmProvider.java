package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import com.exame.ordem.tutor.config.CascataProperties;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;

/** Qualquer provedor com protocolo OpenAI (Groq, Gemini, Mistral, OpenRouter). Instanciado pelo {@link ProvedoresLlm}. */
class OpenAiCompatibleLlmProvider implements LlmProvider {

    private final String id;
    private final Duration ttft;
    private final StreamingChatModel model;

    OpenAiCompatibleLlmProvider(CascataProperties.Provedor cfg) {
        this.id = cfg.id();
        this.ttft = cfg.ttft();

        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(cfg.baseUrl())
                .apiKey(cfg.apiKey())
                .modelName(cfg.model())
                .temperature(cfg.temperature())
                .maxTokens(cfg.maxTokens())
                .timeout(cfg.timeout());

        if (!cfg.headers().isEmpty()) {
            builder.customHeaders(cfg.headers());
        }
        this.model = builder.build();
    }

    @Override public StreamingChatModel streaming() { return model; }
    @Override public String name() { return id; }
    @Override public Duration ttftTimeout() { return ttft; }
}