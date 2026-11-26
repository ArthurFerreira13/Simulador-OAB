package com.exame.ordem.legislacao.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LegislacaoIngestaoProperties.class)
public class LegislacaoConfig {
}