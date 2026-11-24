package com.exame.ordem.legislacao.internal;

import java.text.Normalizer;
import java.util.Locale;

/** Normalizações de texto usadas na resolução de referências e de apelidos de normas. */
public final class TextoBusca {

    private TextoBusca() {}

    /** Minúsculas e sem acento, preservando pontuação (útil para ler "art. 121-A"). */
    public static String minusculoSemAcento(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** Minúsculas, sem acento e sem pontuação, com espaços simples (forma dos apelidos). */
    public static String normalizar(String s) {
        return minusculoSemAcento(s).replaceAll("[\\s\\p{Punct}]+", " ").trim();
    }
}
