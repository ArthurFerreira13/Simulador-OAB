package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Último recurso da cascata, só para desenvolvimento. Liga com OLLAMA_CHAT_HABILITADO=true. */
@Component
@ConditionalOnProperty(name = "app.tutor.ollama-chat.habilitado", havingValue = "true")
class OllamaLlmProvider implements LlmProvider {

    private final StreamingChatModel model;

    OllamaLlmProvider(
            @Value("${app.tutor.ollama-chat.url:http://localhost:11434}") String baseUrl,
            @Value("${app.tutor.ollama-chat.model:llama3.1:8b}") String modelName) {
        this.model = OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.3)
                .numPredict(3000)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    @Override public StreamingChatModel streaming() { return model; }
    @Override public String name() { return "ollama"; }
    @Override public Duration ttftTimeout() { return Duration.ofSeconds(90); } // 1ª chamada carrega o modelo
}