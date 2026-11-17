package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import com.exame.ordem.tutor.config.CascataProperties;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private final String id;
    private final long ttftMs;
    private final int maxOutputTokens;
    private final StreamingChatModel model;

    public OpenAiCompatibleLlmProvider(CascataProperties.Provedor cfg) {
        this.id = cfg.id();
        this.ttftMs = cfg.ttft().toMillis();
        this.maxOutputTokens = cfg.maxTokens();
        this.model = OpenAiStreamingChatModel.builder()
                .baseUrl(cfg.baseUrl())
                .apiKey(cfg.apiKey())
                .modelName(cfg.model())
                .temperature(cfg.temperature())
                .maxTokens(cfg.maxTokens())
                .timeout(cfg.timeout())
                .build();
    }

    @Override public StreamingChatModel streaming() { return model; }
    @Override public String name() { return id; }
    @Override public long ttftMs() { return ttftMs; }
    @Override public int maxOutputTokens() { return maxOutputTokens; }
}