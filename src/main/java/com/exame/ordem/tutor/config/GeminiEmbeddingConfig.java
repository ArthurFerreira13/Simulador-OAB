package com.exame.ordem.tutor.config;

import com.exame.ordem.tutor.internal.ComentariosPendentesRepository;
import com.exame.ordem.tutor.internal.ProvedorEmbedding;
import com.exame.ordem.tutor.service.EmbeddingIndexador;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuração dos beans de embedding do Gemini.
 *
 * <p><b>Condição unificada:</b> o modelo e o store só são criados quando a
 * chave da API está presente. Isso evita a instanciação de um
 * {@code PgVectorEmbeddingStore} sem um modelo correspondente, alinhando
 * a criação dos beans com a lógica do {@code RagProviderResolver}.
 *
 * <p>Beans do Gemini só são criados quando a chave contém texto; uma
 * propriedade vazia equivale a provedor desabilitado.
 *
 * <p><b>Nome da tabela:</b> vem de {@link ProvedorEmbedding#GEMINI#tabela()},
 * fonte única do identificador. Alterar o enum propaga para o store e para
 * o repositório de pendentes simultaneamente.
 *
 * <p><b>{@code @Primary}:</b> Gemini é o embedder padrão quando ambos os
 * provedores estão disponíveis. Toda injeção no projeto usa
 * {@code @Qualifier}; o {@code @Primary} é defesa contra injeções futuras
 * sem qualificador.
 */
@Configuration(proxyBeanMethods = false)
class GeminiEmbeddingConfig {

    @Primary
    @Bean("geminiEmbeddingModel")
    @Conditional(GeminiApiKeyConfiguredCondition.class)
    EmbeddingModel geminiEmbeddingModel(TutorProperties props) {
        var cfg = props.embedding();
        return GoogleGenAiEmbeddingModel.builder()
                .apiKey(cfg.geminiApiKey())
                .modelName(cfg.model())
                .outputDimensionality(cfg.geminiDimension())
                .timeout(cfg.timeout())
                .build();
    }

    @Primary
    @Bean("geminiEmbeddingStore")
    @Conditional(GeminiApiKeyConfiguredCondition.class)
    EmbeddingStore<TextSegment> geminiEmbeddingStore(DataSource dataSource, TutorProperties props) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(ProvedorEmbedding.GEMINI.tabela()) // antes: literal "comentario_embedding_gemini"
                .dimension(props.embedding().geminiDimension())
                .createTable(false)
                .dropTableFirst(false)
                .build();
    }

    @Bean("geminiIndexador")
    @Conditional(GeminiApiKeyConfiguredCondition.class)
    @ConditionalOnProperty(prefix = "app.tutor.gemini-indexador", name = "habilitado", havingValue = "true")
    EmbeddingIndexador geminiIndexador(
            JdbcTemplate jdbc,
            @Qualifier("geminiEmbeddingModel") EmbeddingModel model,
            @Qualifier("geminiEmbeddingStore") EmbeddingStore<TextSegment> store) {

        var provedor = ProvedorEmbedding.GEMINI;
        var repo = new ComentariosPendentesRepository(jdbc, provedor);
        return new EmbeddingIndexador(repo, model, store, provedor.prefixoLog());
    }
}