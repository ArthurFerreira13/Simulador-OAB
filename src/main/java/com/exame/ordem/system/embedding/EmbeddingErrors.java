package com.exame.ordem.system.embedding;

import java.util.Locale;

/**
 * Classifica exceções devolvidas por provedores de embedding.
 *
 * Gemini, Ollama e Voyage embrulham o erro de maneiras diferentes
 * (HTTP status, gRPC status, wrapper de IOException). Sem uma
 * classificação central, cada serviço acaba reinventando heurísticas
 * frágeis — e o pior caso é engolir um 429 e entrar em loop infinito.
 *
 * Classe pura: sem estado, sem Spring, testável em unidade.
 */
public final class EmbeddingErrors {

    private EmbeddingErrors() {
        throw new UnsupportedOperationException("Utility class — não instanciar.");
    }

    /**
     * Retorna {@code true} se a exceção — ou qualquer causa na cadeia —
     * indica rate limit / quota excedida.
     */
    public static boolean isRateLimit(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (matchesRateLimit(t)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static boolean matchesRateLimit(Throwable t) {
        if (t.getClass().getSimpleName().contains("RateLimit")) {
            return true;
        }
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return false;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        return lower.contains("429")
                || lower.contains("resource_exhausted")
                || lower.contains("rate limit")
                || lower.contains("rate_limit")
                || lower.contains("quota exceeded")
                || lower.contains("too many requests");
    }
}