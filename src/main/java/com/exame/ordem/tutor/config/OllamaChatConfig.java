package com.exame.ordem.tutor.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do modelo de chat streaming do Ollama.
 *
 * <p><b>Ativação:</b> o bean é criado por padrão. Para desabilitá-lo
 * explicitamente, defina {@code app.tutor.ollama-chat.habilitado=false}.
 * A condição {@code matchIfMissing = true} garante que a ausência da
 * propriedade não impeça a criação do bean, alinhada ao valor padrão
 * {@code true} em {@link TutorProperties.OllamaChat}.
 *
 * <p><b>Propriedades:</b> todos os parâmetros do builder são mapeados
 * a partir de {@link TutorProperties.OllamaChat}, que por sua vez são
 * validados na inicialização do contexto.
 */
@Configuration(proxyBeanMethods = false)
class OllamaChatConfig {

    @Bean("ollamaStreamingChatModel")
    @ConditionalOnProperty(
            prefix = "app.tutor.ollama-chat",
            name = "habilitado",
            havingValue = "true",
            matchIfMissing = true
    )
    StreamingChatModel ollamaStreamingChatModel(TutorProperties props) {
        var cfg = props.ollamaChat();
        return OllamaStreamingChatModel.builder()
                .baseUrl(cfg.url())
                .modelName(cfg.model())
                .temperature(cfg.temperature())
                .numPredict(cfg.numPredict())
                .timeout(cfg.timeout())
                .build();
    }
}