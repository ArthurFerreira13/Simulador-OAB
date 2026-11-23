package com.exame.ordem.tutor.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LlmCooldownPolicyTest {

    private static final String PROVEDOR = "gemini";

    @Test
    void aplicaCooldownDePagamento() {
        ProvedorSaude saude = mock(ProvedorSaude.class);
        policy(saude, Clock.systemUTC()).aplicar(PROVEDOR,
                new RuntimeException("PAYMENT_METHOD_REQUIRED"));

        verify(saude).agendarCooldown(PROVEDOR, Duration.ofHours(1));
    }

    @Test
    void respeitaRetryInformadoArredondandoParaCima() {
        ProvedorSaude saude = mock(ProvedorSaude.class);
        policy(saude, Clock.systemUTC()).aplicar(PROVEDOR,
                new RuntimeException("429: retry in 8.2s"));

        verify(saude).agendarCooldown(PROVEDOR, Duration.ofSeconds(9));
    }

    @Test
    void aplicaMinimoDeCincoSegundosParaRateLimit() {
        ProvedorSaude saude = mock(ProvedorSaude.class);
        policy(saude, Clock.systemUTC()).aplicar(PROVEDOR,
                new RuntimeException("\"Retry-After\":\"2\" 429"));

        verify(saude).agendarCooldown(PROVEDOR, Duration.ofSeconds(5));
    }

    @Test
    void cooldownDeCotaDiariaTerminaNaProximaViradaUtc() {
        ProvedorSaude saude = mock(ProvedorSaude.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-17T15:00:00Z"),
                ZoneId.of("America/Sao_Paulo"));

        policy(saude, clock).aplicar(PROVEDOR,
                new RuntimeException("daily free allocation exhausted"));

        verify(saude).agendarCooldown(PROVEDOR, Duration.ofHours(9));
    }

    @Test
    void naoAgendaCooldownParaErroNaoClassificado() {
        ProvedorSaude saude = mock(ProvedorSaude.class);
        policy(saude, Clock.systemUTC()).aplicar(PROVEDOR,
                new RuntimeException("connection reset"));

        verifyNoInteractions(saude);
    }

    private static LlmCooldownPolicy policy(ProvedorSaude saude, Clock clock) {
        return new LlmCooldownPolicy(saude, clock);
    }
}
