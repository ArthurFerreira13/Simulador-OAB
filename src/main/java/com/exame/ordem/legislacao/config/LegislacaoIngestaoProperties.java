package com.exame.ordem.legislacao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.legislacao.ingestao")
public record LegislacaoIngestaoProperties(
        Path diretorioCache,
        int maxTentativas,
        Duration intervaloBase,
        Duration connectTimeout,
        Duration requestTimeout,
        /** Job em andamento sem atividade por mais que isso é considerado órfão (processo caiu no meio). */
        Duration jobTimeout
) {
    public LegislacaoIngestaoProperties {
        diretorioCache = diretorioCache != null ? diretorioCache : Path.of("data/cache-legislacao");
        maxTentativas = maxTentativas > 0 ? maxTentativas : 3;
        intervaloBase = intervaloBase != null ? intervaloBase : Duration.ofSeconds(2);
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(15);
        requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(30);
        jobTimeout = jobTimeout != null && jobTimeout.isPositive() ? jobTimeout : Duration.ofMinutes(15);
    }
}