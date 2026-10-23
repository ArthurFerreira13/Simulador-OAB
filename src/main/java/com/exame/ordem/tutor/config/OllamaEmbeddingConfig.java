package com.exame.ordem.tutor.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Ollama como provedor de embeddings: local (http://localhost:11434), sem cota e sem custo.
 * Serve para indexar os comentários e para a busca RAG em desenvolvimento; não vai para produção.
 */
@Configuration
class OllamaEmbeddingConfig {

    @Bean("ollamaEmbeddingModel")
    EmbeddingModel ollamaEmbeddingModel(
            @Value("${app.tutor.embedding.ollama-url:http://localhost:11434}") String baseUrl,
            @Value("${app.tutor.embedding.ollama-model:nomic-embed-text}") String modelName) {

        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean("ollamaEmbeddingStore")
    EmbeddingStore<TextSegment> ollamaEmbeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("comentario_embedding_ollama")
                .dimension(768)
                .createTable(false)
                .dropTableFirst(false)
                .build();
    }
}