package com.exame.ordem.legislacao.internal;

/**
 * Monta o texto que vai ao modelo de embedding: norma + caminho hierárquico (+ contexto do dispositivo
 * pai, para parágrafos, incisos e alíneas) + texto do dispositivo. Assim um inciso ("XI - a casa é asilo...")
 * não perde o "Art. 5º" e a norma ao ser buscado isoladamente. Mudar esta montagem exige nova VERSAO.
 *
 * <p>Os limites de tamanho são provisórios: a especificação manda definir chunk com benchmark em dados reais.
 */
public final class Contextualizador {

    public static final String VERSAO = "ctx-1";
    static final int MAX_CONTEXTO_PAI = 600;
    static final int MAX_TOTAL = 8_000;

    private Contextualizador() {}

    public static String montar(String nomeNorma, String caminho, String textoPai, String texto) {
        StringBuilder sb = new StringBuilder();
        sb.append(compactar(nomeNorma)).append('\n').append(compactar(caminho)).append('\n');
        if (textoPai != null && !textoPai.isBlank()) {
            sb.append("Contexto: ").append(truncar(compactar(textoPai), MAX_CONTEXTO_PAI)).append('\n');
        }
        sb.append(compactar(texto));
        return truncar(sb.toString(), MAX_TOTAL);
    }

    /** Hash do que foi efetivamente embutido: muda se o texto, o modelo ou a versão do pipeline mudarem. */
    public static String hash(String modelo, String versaoPipeline, String textoContextualizado) {
        return Hashes.sha256(versaoPipeline + "|" + modelo + "|" + textoContextualizado);
    }

    private static String compactar(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").strip();
    }

    private static String truncar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max).stripTrailing();
    }
}
