package com.exame.ordem.legislacao.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita o agendamento da varredura de embeddings (idempotente se já estiver habilitado em outro módulo). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class LegislacaoSchedulingConfig {
}
