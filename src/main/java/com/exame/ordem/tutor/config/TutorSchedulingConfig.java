package com.exame.ordem.tutor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Garante o agendamento usado pela limpeza e pelos contadores do cache semântico. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class TutorSchedulingConfig {
}
