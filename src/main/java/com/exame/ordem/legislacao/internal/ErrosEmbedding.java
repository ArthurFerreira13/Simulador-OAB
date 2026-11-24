package com.exame.ordem.legislacao.internal;

import java.util.regex.Pattern;

/** Classificação de falhas do provedor de embedding. */
public final class ErrosEmbedding {

    private static final Pattern RATE_LIMIT = Pattern.compile(
            "(?:http|status|code|error)\\D{0,6}\\b429\\b|resource_exhausted|rate[ _-]?limit|too many requests|quota",
            Pattern.CASE_INSENSITIVE);

    private ErrosEmbedding() {}

    /** Percorre a cadeia de causas; "429" solto (ex.: parte de um id) não conta. */
    public static boolean ehRateLimit(Throwable erro) {
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t.getMessage() != null && RATE_LIMIT.matcher(t.getMessage()).find()) {
                return true;
            }
        }
        return false;
    }
}
