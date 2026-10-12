package com.exame.ordem.system;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Fuso oficial de negócio do produto (D30).
 *
 * Todo cálculo sensível a "dia" (limite diário, trial reverso, estado de
 * acesso) usa este Clock. Não usar {@code LocalDate.now()} direto — sempre
 * injetar o Clock, para que testes de fronteira sejam determinísticos.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId FUSO_NEGOCIO = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock clock() {
        return Clock.system(FUSO_NEGOCIO);
    }
}