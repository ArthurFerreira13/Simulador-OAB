package com.exame.ordem.legislacao.internal;

import java.util.stream.Collectors;

/**
 * Normalização determinística antes do hash: remove só diferenças irrelevantes
 * (quebras de linha, espaços duplicados, NBSP). Não altera o conteúdo jurídico.
 */
public final class TextoNormalizador {

    private TextoNormalizador() {}

    public static String normalizar(String texto) {
        if (texto == null) return "";
        String s = texto
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t]+", " ");
        s = s.lines()
                .map(String::strip)
                .collect(Collectors.joining("\n"));
        return s.replaceAll("\n{3,}", "\n\n").strip();
    }
}
