package com.exame.ordem.legislacao.internal;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte o HTML da "Compilação Monovigente" (normas.leg.br,
 * GET /api/public/binario/{id}/texto) em dispositivos: artigo, parágrafo, inciso, alínea e item.
 *
 * Formato observado (Código Civil): um {@code <p>} por dispositivo; o texto vigente fica em
 * {@code <span>} e as anotações de vigência em {@code <span class="Hyperlink">(Revogado pela Lei ...)}.
 * Não há ids nem classes por dispositivo, então o reconhecimento é por regex sobre o início do parágrafo.
 *
 * Regras:
 * - anotação = span.Hyperlink cujo texto começa com "(" (sai do texto do dispositivo);
 * - revogado = conteúdo vazio com anotação "(Revogado ...)"/"(Vetado ...)", ou conteúdo que já começa assim;
 * - parágrafo não reconhecido NÃO é anexado a nada: vai para {@code naoReconhecidos} para revisão.
 *
 * As regexes usam escapes unicode (codepoints) para não depender do encoding do arquivo-fonte.
 */
final class ParserCompilacaoCamara {

    enum Tipo { ARTIGO, PARAGRAFO, INCISO, ALINEA, ITEM }

    record Dispositivo(int ordem, Tipo tipo, String rotulo, String caminho, String texto,
                       List<String> anotacoes, boolean revogado, List<String> hierarquia) {

        Dispositivo comAnotacoes(List<String> extras) {
            List<String> todas = new ArrayList<>(anotacoes);
            todas.addAll(extras);
            return new Dispositivo(ordem, tipo, rotulo, caminho, texto, List.copyOf(todas),
                    calcularRevogado(texto, todas), hierarquia);
        }
    }

    record Resultado(List<Dispositivo> dispositivos, List<String> naoReconhecidos) { }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern HEADING = Pattern.compile(
            "^(PARTE\\s+(?:GERAL|ESPECIAL)"
                    + "|LIVRO\\s+(?:[IVXLCDM]+|[\u00DAU]NICO|COMPLEMENTAR)"
                    + "|T[\u00CDI]TULO\\s+(?:[IVXLCDM]+(?:-[A-Z])?|[\u00DAU]NICO)"
                    + "|CAP[\u00CDI]TULO\\s+(?:[IVXLCDM]+(?:-[A-Z])?|[\u00DAU]NICO)"
                    + "|SE[\u00C7C][\u00C3A]O\\s+(?:[IVXLCDM]+(?:-[A-Z])?|[\u00DAU]NICA)"
                    + "|SUBSE[\u00C7C][\u00C3A]O\\s+(?:[IVXLCDM]+(?:-[A-Z])?|[\u00DAU]NICA))"
                    + "(?:\\s*[-\u2013:.]\\s*|\\s+|$)(.*)$", FLAGS);

    private static final Pattern ARTIGO = Pattern.compile(
            "^Art\\.\\s*(\\d+(?:\\.\\d{3})*)\\s*[\u00BA\u00B0]?(?:-([A-Z]{1,2})\\b)?\\s*[.\\-\u2013]?\\s*(.*)$", FLAGS);

    private static final Pattern PAR_UNICO = Pattern.compile(
            "^Par\u00E1grafo\\s+\u00FAnico\\s*[.\\-\u2013:]?\\s*(.*)$", FLAGS);

    private static final Pattern PARAGRAFO = Pattern.compile(
            "^\u00A7\\s*(\\d+)\\s*[\u00BA\u00B0]?(?:-([A-Z]{1,2})\\b)?\\s*[.\\-\u2013]?\\s*(.*)$");

    private static final Pattern INCISO = Pattern.compile(
            "^([IVXLCDM]+)(?:-([A-Z]{1,2})\\b)?\\s*[-\u2013.]\\s*(.*)$");

    private static final Pattern ALINEA = Pattern.compile("^([a-z])\\)\\s*(.*)$");

    private static final Pattern ITEM = Pattern.compile("^(\\d+)\\s*[.\\-\u2013)]\\s+(.*)$");

    private static final Pattern ASSINATURA = Pattern.compile("^Bras[\u00EDi]lia,", FLAGS);

    private static final Pattern REVOGADO_TEXTO = Pattern.compile(
            "^\\(?\\s*(?:Revogad[oa]|Vetad[oa])", FLAGS);

    private static final Pattern REVOGADO_ANOTACAO = Pattern.compile(
            "^\\(\\s*(?:Revogad[oa]|Vetad[oa])", FLAGS);

    private final List<Dispositivo> saida = new ArrayList<>();
    private final List<String> naoReconhecidos = new ArrayList<>();
    private final Map<String, Integer> vistos = new HashMap<>();
    private final String[] niveis = new String[6]; // PARTE, LIVRO, TITULO, CAPITULO, SECAO, SUBSECAO
    private int nivelPendente = -1;
    private String artigo;
    private String paragrafo;
    private String inciso;
    private String alinea;

    private ParserCompilacaoCamara() { }

    static Resultado parse(String html) {
        return new ParserCompilacaoCamara().executar(html);
    }

    private Resultado executar(String html) {
        for (Element p : Jsoup.parse(html).select("p")) {
            Element copia = p.clone();
            List<String> anotacoes = new ArrayList<>();
            for (Element s : copia.select("span.Hyperlink")) {
                String t = limpar(s.text());
                if (t.startsWith("(")) {
                    anotacoes.add(t);
                    s.remove();
                }
            }
            String texto = limpar(copia.text());

            if (texto.isEmpty()) {
                anexarAoUltimo(anotacoes);
                continue;
            }
            if (!saida.isEmpty() && ASSINATURA.matcher(texto).find()) {
                break;
            }

            int pendente = nivelPendente;
            nivelPendente = -1;

            if (processar(texto, anotacoes)) {
                continue;
            }
            if (pendente >= 0 && texto.length() <= 200) {
                niveis[pendente] = niveis[pendente] + " - " + texto;
                continue;
            }
            naoReconhecidos.add(texto.length() > 160 ? texto.substring(0, 160) + "\u2026" : texto);
        }
        return new Resultado(List.copyOf(saida), List.copyOf(naoReconhecidos));
    }

    private boolean processar(String texto, List<String> anotacoes) {
        Matcher m = HEADING.matcher(texto);
        if (m.matches()) {
            cabecalho(m);
            return true;
        }

        m = ARTIGO.matcher(texto);
        if (m.matches()) {
            artigo = "art" + m.group(1).replace(".", "") + (m.group(2) != null ? "-" + m.group(2).toUpperCase(Locale.ROOT) : "");
            paragrafo = null;
            inciso = null;
            alinea = null;
            registrar(Tipo.ARTIGO, texto, m.start(3), artigo, m.group(3), anotacoes);
            return true;
        }

        if (artigo == null) {
            return false; // nada antes do primeiro artigo (preâmbulo, ementa) vira dispositivo
        }

        m = PAR_UNICO.matcher(texto);
        if (m.matches()) {
            paragrafo = artigo + "_paru";
            inciso = null;
            alinea = null;
            registrar(Tipo.PARAGRAFO, texto, m.start(1), paragrafo, m.group(1), anotacoes);
            return true;
        }

        m = PARAGRAFO.matcher(texto);
        if (m.matches()) {
            paragrafo = artigo + "_par" + m.group(1) + (m.group(2) != null ? "-" + m.group(2).toUpperCase(Locale.ROOT) : "");
            inciso = null;
            alinea = null;
            registrar(Tipo.PARAGRAFO, texto, m.start(3), paragrafo, m.group(3), anotacoes);
            return true;
        }

        m = INCISO.matcher(texto);
        if (m.matches()) {
            String base = paragrafo != null ? paragrafo : artigo + "_cpt";
            inciso = base + "_inc" + romano(m.group(1)) + (m.group(2) != null ? "-" + m.group(2) : "");
            alinea = null;
            registrar(Tipo.INCISO, texto, m.start(3), inciso, m.group(3), anotacoes);
            return true;
        }

        m = ALINEA.matcher(texto);
        if (m.matches()) {
            String base = inciso != null ? inciso : (paragrafo != null ? paragrafo : artigo + "_cpt");
            alinea = base + "_ali_" + m.group(1);
            registrar(Tipo.ALINEA, texto, m.start(2), alinea, m.group(2), anotacoes);
            return true;
        }

        m = ITEM.matcher(texto);
        if (m.matches() && (alinea != null || inciso != null)) {
            String base = alinea != null ? alinea : inciso;
            registrar(Tipo.ITEM, texto, m.start(2), base + "_ite" + m.group(1), m.group(2), anotacoes);
            return true;
        }
        return false;
    }

    private void cabecalho(Matcher m) {
        int n = nivel(m.group(1));
        String rotulo = limpar(m.group(1)).toUpperCase(Locale.ROOT);
        String resto = limpar(m.group(2));
        niveis[n] = resto.isEmpty() ? rotulo : rotulo + " - " + resto;
        for (int k = n + 1; k < niveis.length; k++) {
            niveis[k] = null;
        }
        nivelPendente = resto.isEmpty() ? n : -1; // o título costuma vir no parágrafo seguinte
    }

    private void registrar(Tipo tipo, String textoCompleto, int inicioConteudo, String caminhoBase,
                           String conteudo, List<String> anotacoes) {
        String rotulo = limpar(textoCompleto.substring(0, inicioConteudo));
        String texto = limpar(conteudo);
        int repeticoes = vistos.merge(caminhoBase, 1, Integer::sum);
        String caminho = repeticoes == 1 ? caminhoBase : caminhoBase + "~" + repeticoes;

        List<String> hierarquia = new ArrayList<>();
        for (String n : niveis) {
            if (n != null) {
                hierarquia.add(n);
            }
        }
        saida.add(new Dispositivo(saida.size() + 1, tipo, rotulo, caminho, texto,
                List.copyOf(anotacoes), calcularRevogado(texto, anotacoes), List.copyOf(hierarquia)));
    }

    private void anexarAoUltimo(List<String> anotacoes) {
        if (anotacoes.isEmpty() || saida.isEmpty()) {
            return;
        }
        int i = saida.size() - 1;
        saida.set(i, saida.get(i).comAnotacoes(anotacoes));
    }

    private static boolean calcularRevogado(String texto, List<String> anotacoes) {
        if (REVOGADO_TEXTO.matcher(texto).find()) {
            return true;
        }
        return texto.isBlank() && anotacoes.stream().anyMatch(a -> REVOGADO_ANOTACAO.matcher(a).find());
    }

    private static int nivel(String rotulo) {
        String r = rotulo.toUpperCase(Locale.ROOT);
        if (r.startsWith("PARTE")) return 0;
        if (r.startsWith("LIVRO")) return 1;
        if (r.startsWith("SUBSE")) return 5;
        if (r.startsWith("SE")) return 4;
        if (r.startsWith("CAP")) return 3;
        return 2; // T\u00CDTULO
    }

    private static int romano(String s) {
        int total = 0;
        int anterior = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> 0;
            };
            if (v < anterior) {
                total -= v;
            } else {
                total += v;
                anterior = v;
            }
        }
        return total;
    }

    private static String limpar(String s) {
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}