package com.exame.ordem.tutor.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CascataProperties.class)
class LlmCascadeConfig {

    @Bean
    CircuitBreakerRegistry llmCircuitBreakers() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60)) // 429 de free tier: dar tempo ao limite
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
    }
}