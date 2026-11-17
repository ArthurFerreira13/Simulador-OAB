package com.exame.ordem.tutor.config;

import com.exame.ordem.tutor.internal.ComentariosPendentesRepository;
import com.exame.ordem.tutor.internal.ProvedorEmbedding;
import com.exame.ordem.tutor.service.EmbeddingIndexador;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;

@Configuration
class OllamaEmbeddingConfig {

    @Bean("ollamaEmbeddingModel")
    EmbeddingModel ollamaEmbeddingModel(TutorProperties props) {
        var cfg = props.embedding();
        return OllamaEmbeddingModel.builder()
                .baseUrl(cfg.ollamaUrl())
                .modelName(cfg.ollamaModel())
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean("ollamaEmbeddingStore")
    EmbeddingStore<TextSegment> ollamaEmbeddingStore(DataSource dataSource, TutorProperties props) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("comentario_embedding_ollama")
                .dimension(props.embedding().ollamaDimension())
                .createTable(false)
                .dropTableFirst(false)
                .build();
    }

    @Bean("ollamaIndexador")
    @ConditionalOnProperty(prefix = "app.tutor.ollama-indexador", name = "habilitado", havingValue = "true")
    EmbeddingIndexador ollamaIndexador(
            JdbcTemplate jdbc,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel model,
            @Qualifier("ollamaEmbeddingStore") EmbeddingStore<TextSegment> store) {

        var provedor = ProvedorEmbedding.OLLAMA;
        var repo = new ComentariosPendentesRepository(jdbc, provedor);
        return new EmbeddingIndexador(repo, model, store, provedor.prefixoLog());
    }
}