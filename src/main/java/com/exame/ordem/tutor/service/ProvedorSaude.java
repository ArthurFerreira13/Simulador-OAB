package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.CascataProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Responde se um provedor pode ser tentado agora. Une as duas checagens sobre
 * o mesmo recurso — o provedor — que antes viviam em classes separadas:
 * cooldown reativo (429/401/403/404) e limites locais de RPM/RPD.
 *
 * <p>Provedor ausente de {@code CascataProperties} (ex.: Ollama) é tratado
 * como "sem limite local": o cooldown reativo e o circuit breaker ainda o
 * protegem, mas não há balde de RPM/RPD para ele.
 */
@Component
public class ProvedorSaude {

    public enum Estado {
        OK(null),
        EM_COOLDOWN("cooldown"),
        LIMITE_ATINGIDO("rate_limit");

        private final String motivo;

        Estado(String motivo) {
            this.motivo = motivo;
        }

        public String motivo() {
            return motivo;
        }
    }

    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Bucket> baldesRpm = new ConcurrentHashMap<>();
    private final Map<String, Bucket> baldesRpd = new ConcurrentHashMap<>();
    private final Map<String, CascataProperties.Provedor> porId;

    ProvedorSaude(CascataProperties props) {
        this.porId = props.provedores().stream()
                .collect(Collectors.toMap(
                        CascataProperties.Provedor::id,
                        Function.identity(),
                        (a, b) -> a));
    }

    public Estado verificar(String id) {
        Objects.requireNonNull(id, "id");
        if (emCooldown(id)) {
            return Estado.EM_COOLDOWN;
        }
        if (!dentroDosLimites(id)) {
            return Estado.LIMITE_ATINGIDO;
        }
        return Estado.OK;
    }

    public void agendarCooldown(String id, Duration duracao) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(duracao, "duracao");
        cooldowns.put(id, Instant.now().plus(duracao));
    }

    public Optional<Duration> cooldownRestante(String id) {
        Instant ate = cooldowns.get(id);
        if (ate == null) {
            return Optional.empty();
        }
        Duration restante = Duration.between(Instant.now(), ate);
        if (restante.isNegative()) {
            cooldowns.remove(id, ate);
            return Optional.empty();
        }
        return Optional.of(restante);
    }

    private boolean emCooldown(String id) {
        return cooldownRestante(id).isPresent();
    }

    private boolean dentroDosLimites(String id) {
        CascataProperties.Provedor cfg = porId.get(id);
        if (cfg == null) {
            return true;
        }
        return consome(cfg.rpm(), Duration.ofMinutes(1), baldesRpm, id)
                && consome(cfg.rpd(), Duration.ofDays(1), baldesRpd, id);
    }

    private static boolean consome(int capacidade, Duration janela,
                                   Map<String, Bucket> baldes, String id) {
        if (capacidade <= 0) {
            return true;
        }
        Bucket balde = baldes.computeIfAbsent(id, k -> criarBucket(capacidade, janela));
        return balde.tryConsume(1);
    }

    private static Bucket criarBucket(int capacidade, Duration janela) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacidade)
                        .refillGreedy(capacidade, janela)
                        .build())
                .build();
    }
}