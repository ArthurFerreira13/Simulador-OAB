package com.exame.ordem.tutor.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Beans de embedding do Gemini. Ambos são {@code @Primary} para que autoconfigurações do
 * LangChain4j resolvam a injeção por tipo sem ambiguidade; os {@code @Qualifier} explícitos
 * continuam valendo. O store usa o mesmo {@code DataSource} do Spring.
 */
@Configuration
class GeminiEmbeddingConfig {

    @Primary
    @Bean("geminiEmbeddingModel")
    EmbeddingModel geminiEmbeddingModel(
            @Value("${app.tutor.embedding.gemini-api-key:}") String apiKey,
            @Value("${app.tutor.embedding.model:gemini-embedding-001}") String modelName) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY não configurada. Coloque no .env: GEMINI_API_KEY=AIza...");
        }

        return GoogleGenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .outputDimensionality(768)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Primary
    @Bean("geminiEmbeddingStore")
    EmbeddingStore<TextSegment> geminiEmbeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("comentario_embedding_gemini")
                .dimension(768)
                .createTable(false)
                .dropTableFirst(false)
                .build();
    }
}