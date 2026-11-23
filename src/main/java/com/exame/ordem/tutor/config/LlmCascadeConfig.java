package com.exame.ordem.tutor.config;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuração de resiliência para os provedores de LLM.
 *
 * <p><b>Atenção sobre externalização:</b> os valores abaixo são hardcoded
 * nesta classe. Como {@code CircuitBreakerRegistry} e {@code BulkheadRegistry}
 * são definidos como {@code @Bean}, a auto-configuração do
 * {@code resilience4j-spring-boot-starter} recua (back-off via
 * {@code @ConditionalOnMissingBean}) e as propriedades {@code resilience4j.*}
 * do {@code application.yml} NÃO são aplicadas a estas instâncias. Se a
 * externalização for necessária, remova esta classe e deixe o starter
 * auto-configurar, lendo tudo do YAML.
 *
 * <p><b>Circuit Breaker (valores atuais):</b> janela de 20 chamadas com
 * mínimo de 10 para evitar abertura por flutuação estatística; threshold
 * de falha 50%; 60s em estado aberto (default do Resilience4j);
 * 5 chamadas de teste em meio-aberto (default é 10).
 *
 * <p><b>Bulkhead — valor default (25 concorrentes):</b> serve os provedores
 * OpenAI-compatíveis da cascata (Groq, NVIDIA, Gemini, FreeLLM, etc.).
 * Esses provedores têm limites de cota próprios, mas tipicamente não
 * derrubam a conexão por concorrência — o gargalo real é 429 por RPM/RPD,
 * tratado pelo circuit breaker.
 *
 * <p><b>Bulkhead — override do Ollama (5 concorrentes):</b> o Ollama local
 * foi medido para aguentar ~5 requisições simultâneas antes de degradar.
 * O bulkhead nomeado {@code "ollama"} limita as chamadas concorrentes a 5
 * e usa {@code maxWaitDuration(ZERO)} para <em>fail-fast</em>: quando as
 * 5 vagas estão ocupadas, {@code tryAcquirePermission()} devolve
 * {@code false} imediatamente e o {@code LlmRouter} avança para o próximo
 * provedor da cascata em vez de enfileirar no Ollama.
 *
 * <p><b>Nota sobre {@code maxWaitDuration}:</b> o {@code LlmRouter} usa
 * {@code Bulkhead.tryAcquirePermission()}, que é <em>não-bloqueante</em>
 * e ignora {@code maxWaitDuration}. Por isso o valor é {@code ZERO} em
 * ambas as configs: reflete o comportamento real e evita a ilusão de que
 * existe fila no cliente. O Ollama, por sua vez, tem a própria fila
 * controlada por {@code OLLAMA_MAX_QUEUE} — que, com o bulkhead de 5,
 * nunca vai receber tráfego suficiente para encher.
 *
 * <p><b>Alinhamento com o servidor Ollama:</b> esta config (5) deve casar
 * com {@code OLLAMA_NUM_PARALLEL=5} no processo do Ollama. Se o servidor
 * estiver configurado com menos paralelismo que o bulkhead, o excesso
 * enfileira lá; se estiver com mais, o bulkhead se torna o gargalo real
 * (e é a intenção: o cliente é quem decide pular de provedor).
 */
@Configuration(proxyBeanMethods = false)
class LlmCascadeConfig {

    private static final int OLLAMA_MAX_CONCURRENT = 5;

    @Bean
    CircuitBreakerRegistry llmCircuitBreakers() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(5)
                .build());
    }

    @Bean
    BulkheadRegistry llmBulkheads(CascataProperties cascata) {
        var defaultConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(cascata.bulkheadMaxConcorrente())
                .maxWaitDuration(Duration.ZERO)
                .build();

        var ollamaConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(OLLAMA_MAX_CONCURRENT)
                .maxWaitDuration(Duration.ZERO)
                .build();

        var registry = BulkheadRegistry.of(defaultConfig);
        // Override por nome: BulkheadRegistry.bulkhead("ollama") devolve esta
        // config; os demais provedores herdam a defaultConfig (25).
        registry.bulkhead("ollama", ollamaConfig);
        return registry;
    }
}