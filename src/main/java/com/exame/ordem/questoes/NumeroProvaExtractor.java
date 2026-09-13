package com.exame.ordem.questoes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NumeroProvaExtractor {

    private static final Pattern PADRAO = Pattern.compile("^(\\d+)");

    private NumeroProvaExtractor() {
    }

    public static Integer extrair(String titulo) {
        Matcher m = PADRAO.matcher(titulo);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }
}