package com.exame.ordem.tutor.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Beans do provedor FreeLLMAPI (proxy OpenAI-compatível local).
 *
 * <p><b>Assimetria intencional entre chat e embedding:</b>
 * o {@code freeLlmApiStreamingChatModel} é sempre criado (não tem
 * {@code @ConditionalOnProperty}) porque é o fallback local do
 * {@code LlmRouter}. O {@code freeLlmEmbeddingModel} e o
 * {@code freeLlmEmbeddingStore} só existem quando
 * {@code app.tutor.freellmapi.autostart=true}, porque exigem o proxy
 * rodando para responder a {@code /embeddings}. Hoje o RAG não usa
 * FreeLLM como provedor de embedding; se passar a usar, é preciso
 * garantir que {@code autostart} esteja ligado ou trocar a injeção
 * para {@code ObjectProvider}.
 *
 * <p><b>API key opcional:</b> tanto o chat model quanto o embedding
 * model usam {@code "not-configured"} quando a chave não está
 * definida. A documentação do LangChain4j exige um valor não-nulo
 * para APIs OpenAI-compatíveis, mesmo as locais sem autenticação.
 */
@Configuration(proxyBeanMethods = false)
class FreeLlmApiConfig {

    @Bean("freeLlmApiStreamingChatModel")
    StreamingChatModel freeLlmApiStreamingChatModel(TutorProperties props) {
        var cfg = props.freellmapi();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(cfg.url())
                .apiKey(cfg.habilitado() ? cfg.key() : "not-configured")
                .modelName(cfg.chatModel())
                .maxTokens(cfg.maxTokens())
                .timeout(cfg.timeout())
                .build();
    }

    @Bean("freeLlmEmbeddingModel")
    @ConditionalOnProperty(prefix = "app.tutor.freellmapi", name = "autostart", havingValue = "true")
    EmbeddingModel freeLlmEmbeddingModel(TutorProperties props) {
        var cfg = props.freellmapi();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(cfg.url())
                .apiKey(cfg.habilitado() ? cfg.key() : "not-configured") // ← antes: cfg.key() direto
                .modelName(cfg.embeddingModel())
                .timeout(cfg.timeout()) // ← antes: Duration.ofSeconds(30) fixo
                .build();
    }

    @Bean("freeLlmEmbeddingStore")
    @ConditionalOnProperty(prefix = "app.tutor.freellmapi", name = "autostart", havingValue = "true")
    EmbeddingStore<TextSegment> freeLlmEmbeddingStore(DataSource dataSource, TutorProperties props) {
        var cfg = props.freellmapi();
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("comentario_embedding_freellm")
                .dimension(cfg.embeddingDimension()) // ← requer propriedade nova
                .createTable(false)
                .dropTableFirst(false)
                .build();
    }
}