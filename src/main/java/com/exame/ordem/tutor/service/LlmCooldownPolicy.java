package com.exame.ordem.tutor.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class LlmCooldownPolicy {

    private static final Pattern RETRY = Pattern.compile(
            "(?:try again|retry) in ([0-9]+(?:\\.[0-9]+)?)s|\"Retry-After\":\"(\\d+)\"",
            Pattern.CASE_INSENSITIVE);

    /**
     * "429" só conta quando aparece como status HTTP/código de erro, não como dígitos soltos
     * (ex.: "request id 14290" ou contagem de tokens).
     */
    private static final Pattern STATUS_429 = Pattern.compile(
            "(?:http|status|code|error)\\D{0,6}\\b429\\b|\\b429\\s+(?:too many|rate|client error)",
            Pattern.CASE_INSENSITIVE);

    private final ProvedorSaude saude;
    private final Clock clock;

    LlmCooldownPolicy(ProvedorSaude saude, Clock clock) {
        this.saude = saude;
        this.clock = clock;
    }

    void aplicar(String provedorId, Throwable error) {
        String mensagem = String.valueOf(error.getMessage());
        Duration duracao = determinarDuracao(mensagem);
        if (duracao != null) {
            saude.agendarCooldown(provedorId, duracao);
        }
    }

    private Duration determinarDuracao(String mensagem) {
        if (mensagem.contains("PAYMENT_METHOD_REQUIRED") || mensagem.contains("\"code\":402")) {
            return Duration.ofHours(1);
        }
        // Modelo aposentado: indisponibilidade longa.
        if (mensagem.contains("end of life") || mensagem.contains("\"status\":410")) {
            return Duration.ofHours(6);
        }
        // 404 costuma ser rota/modelo errado ou falha transitória de gateway: pausa curta,
        // não 6 horas.
        if (mensagem.contains("404 page not found") || mensagem.contains("\"code\":404")) {
            return Duration.ofMinutes(30);
        }
        if (mensagem.contains("daily free allocation")) {
            Instant agora = clock.instant();
            Instant proximaViradaUtc = LocalDate.ofInstant(agora, ZoneOffset.UTC)
                    .plusDays(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();
            return Duration.between(agora, proximaViradaUtc);
        }
        if (mensagem.contains("API calls / month")) {
            return Duration.ofHours(12);
        }
        if (STATUS_429.matcher(mensagem).find() || mensagem.contains("rate_limit")
                || mensagem.contains("RESOURCE_EXHAUSTED") || mensagem.contains("Rate limit")
                || mensagem.contains("rate_limited") || mensagem.toLowerCase().contains("too many requests")) {
            return duracaoDeRateLimit(mensagem);
        }
        return null;
    }

    private Duration duracaoDeRateLimit(String mensagem) {
        Matcher matcher = RETRY.matcher(mensagem);
        long segundos = 30;
        if (matcher.find()) {
            String valor = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            segundos = (long) Math.ceil(Double.parseDouble(valor));
        }
        return Duration.ofSeconds(Math.max(5, segundos));
    }
}
