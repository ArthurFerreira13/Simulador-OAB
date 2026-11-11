package com.exame.ordem.tutor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ProvedorSaude {

    private static final Logger log = LoggerFactory.getLogger(ProvedorSaude.class);

    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> latencias = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> falhasConsecutivasPing = new ConcurrentHashMap<>();

    private static final int LIMITE_FALHAS_PING = 3;

    public enum Estado {
        OK("Disponível"),
        EM_COOLDOWN("Em cooldown"),
        SEM_PING("Ping falhou 3+ vezes");

        private final String motivo;
        Estado(String motivo) { this.motivo = motivo; }
        public String motivo() { return motivo; }
    }

    public Estado verificar(String provedorId) {
        // 1. Cooldown de chat (429/503) tem prioridade
        Instant fimCooldown = cooldowns.get(provedorId);
        if (fimCooldown != null && Instant.now().isBefore(fimCooldown)) {
            return Estado.EM_COOLDOWN;
        }

        // 2. Só bloqueia se o ping falhou 3 vezes seguidas
        AtomicInteger falhas = falhasConsecutivasPing.get(provedorId);
        if (falhas != null && falhas.get() >= LIMITE_FALHAS_PING) {
            return Estado.SEM_PING;
        }

        return Estado.OK;
    }

    public void agendarCooldown(String provedorId, Duration duracao) {
        cooldowns.put(provedorId, Instant.now().plus(duracao));
        log.warn("Provedor {} em cooldown por {}s", provedorId, duracao.getSeconds());
    }

    public void registrarSucessoPing(String provedorId, long latenciaMs) {
        latencias.put(provedorId, latenciaMs);
        falhasConsecutivasPing.remove(provedorId); // Zera as falhas
    }

    public void registrarFalhaPing(String provedorId) {
        int novasFalhas = falhasConsecutivasPing
                .computeIfAbsent(provedorId, k -> new AtomicInteger(0))
                .incrementAndGet();
        log.debug("Provedor {} falha no ping ({}/{})", provedorId, novasFalhas, LIMITE_FALHAS_PING);
    }

    public long latencia(String provedorId) {
        // Quem nunca respondeu ao ping vai para o final da fila, mas ainda é elegível
        return latencias.getOrDefault(provedorId, 9999L);
    }
}