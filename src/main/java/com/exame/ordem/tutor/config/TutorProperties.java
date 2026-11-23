package com.exame.ordem.tutor.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.tutor")
public record TutorProperties(
        @NestedConfigurationProperty @Valid FreeLlmApi freellmapi,
        @NestedConfigurationProperty @Valid OllamaChat ollamaChat,
        @NestedConfigurationProperty @Valid Embedding embedding,
        @NestedConfigurationProperty @Valid Indexador geminiIndexador,
        @NestedConfigurationProperty @Valid Indexador ollamaIndexador,
        @Valid @DefaultValue Admissao admissao,
        @Valid @DefaultValue Sse sse
) {

    public record FreeLlmApi(
            @DefaultValue("false") boolean autostart,
            @NotBlank @DefaultValue("infra/freellmapi-main") String dir,
            @Positive @DefaultValue("3001") int porta,
            @NotBlank @DefaultValue("http://localhost:3001/v1") String url,
            String key,
            @NotBlank @DefaultValue("auto") String chatModel,
            @NotBlank @DefaultValue("text-embedding-3-small") String embeddingModel,
            @Positive @DefaultValue("1536") int embeddingDimension,
            @Positive @DefaultValue("4096") int maxTokens,
            @DurationMin(millis = 1) @DefaultValue("120s") Duration timeout
    ) {
        public boolean habilitado() {
            return key != null && !key.isBlank();
        }
    }

    public record OllamaChat(
            @DefaultValue("true") boolean habilitado,
            @NotBlank @DefaultValue("http://localhost:11434") String url,
            @NotBlank @DefaultValue("qwen2.5:0.5b") String model,
            @DecimalMin("0.0") @DecimalMax("2.0") @DefaultValue("0.3") double temperature,
            @Positive @DefaultValue("1500") int numPredict,
            @DurationMin(millis = 1) @DefaultValue("120s") Duration timeout
    ) {}

    /**
     * Configuração de embeddings (Gemini e Ollama).
     *
     * <p>{@code geminiApiKey} é opcional: ausente ou vazia desabilita o
     * Gemini. O trim no construtor compacto normaliza espaços acidentais.
     */
    public record Embedding(
            String geminiApiKey,
            @NotBlank @DefaultValue("gemini-embedding-001") String model,
            @Positive @DefaultValue("768") int geminiDimension,
            @NotBlank @DefaultValue("http://localhost:11434") String ollamaUrl,
            @NotBlank @DefaultValue("nomic-embed-text") String ollamaModel,
            @Positive @DefaultValue("768") int ollamaDimension,
            @DurationMin(millis = 1) @DefaultValue("30s") Duration timeout,
            @Positive @DefaultValue("2") int maxConcurrency,
            @DurationMin(millis = 1) @DefaultValue("3s") Duration esperaNaFila
    ) {
        public Embedding {
            geminiApiKey = geminiApiKey == null ? null : geminiApiKey.trim();
        }
    }

    /** Controle de admissão das sessões SSE (ver AdmissaoSessoes). Ajuste a capacidade global ao que o pool e os LLMs aguentam. */
    public record Admissao(
            @Positive @DefaultValue("10000") int maxSessoesSimultaneas,
            @Positive @DefaultValue("2") int maxStreamsPorUsuario
    ) {}

    /** Duração máxima de uma resposta em streaming. Deve cobrir maxTokens na velocidade do provedor mais lento. */
    public record Sse(
            @DurationMin(millis = 1000) @DefaultValue("120s") Duration timeout
    ) {}

    public record Indexador(@DefaultValue("false") boolean habilitado) {}
}