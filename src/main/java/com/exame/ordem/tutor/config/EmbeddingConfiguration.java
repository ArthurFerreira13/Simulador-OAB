package com.exame.ordem.tutor.config;

import com.exame.ordem.tutor.internal.ComentariosPendentesRepository;
import com.exame.ordem.tutor.internal.ProvedorEmbedding;
import com.exame.ordem.tutor.service.EmbeddingIndexador;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(RagProperties.class) // <-- ADICIONADO AQUI
public class EmbeddingConfiguration {

    @Bean("geminiIndexador")
    EmbeddingIndexador geminiIndexador(
            JdbcTemplate jdbc,
            @Qualifier("geminiEmbeddingModel") EmbeddingModel model,
            @Qualifier("geminiEmbeddingStore")
            EmbeddingStore<TextSegment> store) {

        var provedor = ProvedorEmbedding.GEMINI;
        var repo = new ComentariosPendentesRepository(jdbc, provedor);

        return new EmbeddingIndexador(repo, model, store, provedor.prefixoLog());
    }

    @Bean("ollamaIndexador")
    EmbeddingIndexador ollamaIndexador(
            JdbcTemplate jdbc,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel model,
            @Qualifier("ollamaEmbeddingStore")
            EmbeddingStore<TextSegment> store) {

        var provedor = ProvedorEmbedding.OLLAMA;
        var repo = new ComentariosPendentesRepository(jdbc, provedor);

        return new EmbeddingIndexador(repo, model, store, provedor.prefixoLog());
    }
}