package com.exame.ordem.tutor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ProvedorSaude {

    private static final Logger log = LoggerFactory.getLogger(ProvedorSaude.class);
    private static final int LIMITE_FALHAS_PING = 3;
    private static final Duration INTERVALO_PING_NORMAL = Duration.ofSeconds(30);
    private static final Duration INTERVALO_RESSURREICAO = Duration.ofMinutes(5);
    private static final long LATENCIA_DESCONHECIDA_MS = 9_999L;

    private final Clock clock;
    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> latencias = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> falhasConsecutivasPing = new ConcurrentHashMap<>();
    private final Map<String, Instant> proximoPing = new ConcurrentHashMap<>();

    public ProvedorSaude(Clock clock) {
        this.clock = clock;
    }

    public enum Estado {
        OK("Disponível"),
        EM_COOLDOWN("Em cooldown"),
        SEM_PING("Ping falhou 3+ vezes");

        private final String motivo;
        Estado(String motivo) { this.motivo = motivo; }
        public String motivo() { return motivo; }
    }

    public Estado verificar(String provedorId) {
        AtomicInteger falhas = falhasConsecutivasPing.get(provedorId);
        if (falhas != null && falhas.get() >= LIMITE_FALHAS_PING) {
            return Estado.SEM_PING;
        }

        // freellmapi é fallback local sem cota externa, imune a cooldown de rate limit
        if ("freellmapi".equals(provedorId)) {
            return Estado.OK;
        }

        Instant fimCooldown = cooldowns.get(provedorId);
        if (fimCooldown != null && Instant.now(clock).isBefore(fimCooldown)) {
            return Estado.EM_COOLDOWN;
        }

        return Estado.OK;
    }

    public boolean devePingar(String provedorId) {
        Instant proximo = proximoPing.get(provedorId);
        return proximo == null || Instant.now(clock).isAfter(proximo);
    }

    public void agendarCooldown(String provedorId, Duration duracao) {
        cooldowns.put(provedorId, Instant.now(clock).plus(duracao));
        log.warn("Provedor {} em cooldown por {}s", provedorId, duracao.toSeconds());
    }

    public void registrarSucessoPing(String provedorId, long latenciaMs) {
        latencias.put(provedorId, latenciaMs);
        falhasConsecutivasPing.remove(provedorId);
        proximoPing.put(provedorId, Instant.now(clock).plus(INTERVALO_PING_NORMAL));
    }

    public void registrarFalhaPing(String provedorId) {
        int novasFalhas = falhasConsecutivasPing
                .computeIfAbsent(provedorId, k -> new AtomicInteger(0))
                .incrementAndGet();

        Instant agora = Instant.now(clock);
        if ("freellmapi".equals(provedorId)) {
            proximoPing.put(provedorId, agora.plus(INTERVALO_PING_NORMAL));
            return;
        }

        if (novasFalhas >= LIMITE_FALHAS_PING) {
            proximoPing.put(provedorId, agora.plus(INTERVALO_RESSURREICAO));
            log.debug("Provedor {} falhou {}+ vezes. Próximo ping em 5 min.", provedorId, LIMITE_FALHAS_PING);
        } else {
            proximoPing.put(provedorId, agora.plus(INTERVALO_PING_NORMAL));
        }
    }

    public long latencia(String provedorId) {
        return latencias.getOrDefault(provedorId, LATENCIA_DESCONHECIDA_MS);
    }
}