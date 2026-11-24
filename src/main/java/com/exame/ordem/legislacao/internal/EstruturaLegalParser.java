package com.exame.ordem.legislacao.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a hierarquia de dispositivos (partes, livros, títulos, capítulos, seções, artigos,
 * parágrafos, incisos e alíneas) de um texto legal já normalizado ({@link TextoNormalizador}).
 *
 * <p>Cada dispositivo guarda só o SEU texto (do marcador até o próximo marcador): o caput de um
 * artigo não inclui os parágrafos, que são dispositivos filhos. As notas editoriais
 * "(Redação dada...)", "(Incluído...)", "(Revogado...)" e "(Vetado)" ficam no texto e são
 * interpretadas: o último estado declarado vence. A AUSÊNCIA de nota nunca vira "vigente" aqui;
 * quem decide isso é a ingestão, conforme a fonte seja consolidada ou não.
 */
public final class EstruturaLegalParser {

    public static final String VERSAO = "estrutura-texto-1";

    public enum Tipo { PARTE, LIVRO, TITULO, CAPITULO, SECAO, SUBSECAO, ARTIGO, PARAGRAFO, INCISO, ALINEA }

    /** Situação declarada pelo próprio texto do dispositivo. UNKNOWN = o texto não declara nada. */
    public enum Situacao { UNKNOWN, REVOGADO, VETADO }

    public enum TipoAlteracao { NENHUMA, REDACAO_DADA, INCLUIDO, RENUMERADO, REVOGADO, VETADO }

    public record Dispositivo(
            Tipo tipo,
            String identificador,
            String chaveLogica,
            String chaveLogicaPai,
            String caminho,
            int ordem,
            String texto,
            Situacao situacao,
            TipoAlteracao tipoAlteracao,
            String normaAlteradora
    ) {}

    public record Resultado(List<Dispositivo> dispositivos, List<String> avisos) {
        public long quantidade(Tipo tipo) {
            return dispositivos.stream().filter(d -> d.tipo() == tipo).count();
        }
    }

    private static final String ORDINAL = "(?:\\u00BA|\\u00B0|o(?!\\p{L}))?";

    private static final Pattern ESTRUTURA = Pattern.compile(
            "^(PARTE|LIVRO|T[\\u00CDI]TULO|CAP[\\u00CDI]TULO|SE[\\u00C7C][\\u00C3A]O|SUBSE[\\u00C7C][\\u00C3A]O)\\s+"
                    + "([IVXLCDM]+(?:-[A-Z])?|[\\u00DAU]NICO|GERAL|ESPECIAL)(?=$|\\s|[-\\u2013\\u2014.])(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIGO = Pattern.compile(
            "^Art\\.?\\s*(\\d+(?:\\.\\d{3})*)\\s*" + ORDINAL + "\\s*(?:-\\s*([A-Z])\\b)?");
    private static final Pattern PARAGRAFO = Pattern.compile(
            "^\\u00A7\\s*(\\d+(?:\\.\\d{3})*)\\s*" + ORDINAL + "\\s*(?:-\\s*([A-Z])\\b)?");
    private static final Pattern PARAGRAFO_UNICO = Pattern.compile(
            "^Par[\\u00E1a]grafo\\s+[\\u00FAu]nico", Pattern.CASE_INSENSITIVE);
    private static final Pattern INCISO = Pattern.compile(
            "^([IVXLCDM]+)(?:\\s*-\\s*([A-Z])\\b)?\\s*[-\\u2013\\u2014]\\s+\\S");
    private static final Pattern ALINEA = Pattern.compile("^([a-z])\\)\\s+\\S");

    private static final Pattern ANOTACAO = Pattern.compile(
            "\\((Reda[\\u00E7c][\\u00E3a]o dada|Inclu[\\u00ED i]d[oa]|Revogad[oa]|Vetad[oa]|Renumerad[oa])([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NORMA_ALTERADORA = Pattern.compile(
            "\\bpel[oa]s?\\s+(.{3,300})$", Pattern.CASE_INSENSITIVE);

    private static final int NIVEIS_ESTRUTURA = 6;

    private EstruturaLegalParser() {}

    public static Resultado analisar(String textoNormalizado) {
        List<No> nos = new ArrayList<>();
        No[] estrutura = new No[NIVEIS_ESTRUTURA];
        No artigo = null, paragrafo = null, inciso = null, atual = null;

        for (String linha : textoNormalizado.split("\n")) {
            if (linha.isBlank()) {
                continue;
            }
            No novo = reconhecer(linha, estrutura, artigo, paragrafo, inciso);
            if (novo == null) {
                if (atual != null) {
                    atual.linhas.add(linha);
                }
                continue;
            }
            switch (novo.tipo) {
                case PARTE, LIVRO, TITULO, CAPITULO, SECAO, SUBSECAO -> {
                    int nivel = novo.tipo.ordinal();
                    for (int i = nivel; i < NIVEIS_ESTRUTURA; i++) estrutura[i] = null;
                    estrutura[nivel] = novo;
                    artigo = paragrafo = inciso = null;
                }
                case ARTIGO -> { artigo = novo; paragrafo = inciso = null; }
                case PARAGRAFO -> { paragrafo = novo; inciso = null; }
                case INCISO -> inciso = novo;
                case ALINEA -> { }
            }
            nos.add(novo);
            atual = novo;
        }
        return montar(nos);
    }

    // ------------------------------------------------------------------ reconhecimento

    private static No reconhecer(String linha, No[] estrutura, No artigo, No paragrafo, No inciso) {
        Matcher m = ESTRUTURA.matcher(linha);
        if (m.matches() && restoValido(m.group(3))) {
            return estrutural(m.group(1), m.group(2), linha, estrutura);
        }
        m = ARTIGO.matcher(linha);
        if (m.find()) {
            String num = m.group(1);
            boolean ordinal = linha.substring(m.start(1) + num.length()).stripLeading()
                    .matches("^(\\u00BA|\\u00B0|o(?!\\p{L})).*");
            String letra = m.group(2);
            String sufixo = (ordinal ? "\u00BA" : "") + (letra != null ? "-" + letra : "");
            String chave = "art-" + num.replace(".", "") + (letra != null ? "-" + letra : "");
            No n = new No(Tipo.ARTIGO, "Art. " + num + sufixo, chave, ultimoEstrutural(estrutura));
            n.linhas.add(linha);
            return n;
        }
        if (artigo == null) {
            return null; // texto anterior ao primeiro artigo (preâmbulo, epígrafe, ementa)
        }
        m = PARAGRAFO_UNICO.matcher(linha);
        if (m.find()) {
            No n = new No(Tipo.PARAGRAFO, "Par\u00E1grafo \u00FAnico", artigo.chave + "/par-unico", artigo);
            n.linhas.add(linha);
            return n;
        }
        m = PARAGRAFO.matcher(linha);
        if (m.find()) {
            String num = m.group(1);
            boolean ordinal = linha.substring(m.start(1) + num.length()).stripLeading()
                    .matches("^(\\u00BA|\\u00B0|o(?!\\p{L})).*");
            String letra = m.group(2);
            String id = "\u00A7 " + num + (ordinal ? "\u00BA" : "") + (letra != null ? "-" + letra : "");
            String chave = artigo.chave + "/par-" + num.replace(".", "") + (letra != null ? "-" + letra : "");
            No n = new No(Tipo.PARAGRAFO, id, chave, artigo);
            n.linhas.add(linha);
            return n;
        }
        m = INCISO.matcher(linha);
        if (m.find()) {
            No pai = paragrafo != null ? paragrafo : artigo;
            String letra = m.group(2);
            String id = m.group(1) + (letra != null ? "-" + letra : "");
            No n = new No(Tipo.INCISO, id, pai.chave + "/inc-" + id, pai);
            n.linhas.add(linha);
            return n;
        }
        m = ALINEA.matcher(linha);
        if (m.find()) {
            No pai = inciso != null ? inciso : (paragrafo != null ? paragrafo : artigo);
            No n = new No(Tipo.ALINEA, m.group(1), pai.chave + "/ali-" + m.group(1), pai);
            n.linhas.add(linha);
            return n;
        }
        return null;
    }

    /** "Capítulo I deste Título..." é texto corrido; "CAPÍTULO I - DOS DIREITOS" é estrutura. */
    private static boolean restoValido(String resto) {
        String r = resto.strip();
        return r.isEmpty() || !Character.isLowerCase(r.codePointAt(0));
    }

    private static No estrutural(String palavra, String numeral, String linha, No[] estrutura) {
        String base = palavra.toUpperCase(Locale.ROOT)
                .replace("\u00CD", "I").replace("\u00C7", "C").replace("\u00C3", "A");
        Tipo tipo = switch (base) {
            case "PARTE" -> Tipo.PARTE;
            case "LIVRO" -> Tipo.LIVRO;
            case "TITULO" -> Tipo.TITULO;
            case "CAPITULO" -> Tipo.CAPITULO;
            case "SECAO" -> Tipo.SECAO;
            default -> Tipo.SUBSECAO;
        };
        String rotulo = switch (tipo) {
            case PARTE -> "Parte";
            case LIVRO -> "Livro";
            case TITULO -> "T\u00EDtulo";
            case CAPITULO -> "Cap\u00EDtulo";
            case SECAO -> "Se\u00E7\u00E3o";
            default -> "Subse\u00E7\u00E3o";
        };
        String num = numeral.toUpperCase(Locale.ROOT).equals("UNICO") || numeral.equalsIgnoreCase("\u00DAnico")
                ? "\u00DAnico" : (numeral.length() > 3 && !numeral.matches("[IVXLCDM-]+[A-Z]?")
                ? capitalizar(numeral) : numeral.toUpperCase(Locale.ROOT));
        String ancestrais = estrutura.length == 0 ? "" : chaveEstruturalAte(estrutura, tipo.ordinal());
        String chave = (ancestrais.isEmpty() ? "" : ancestrais + "/") + prefixo(tipo) + "-" + num.toLowerCase(Locale.ROOT)
                .replace("\u00FA", "u");
        No pai = ancestralEstrutural(estrutura, tipo.ordinal());
        No n = new No(tipo, rotulo + " " + num, chave, pai);
        n.linhas.add(linha);
        return n;
    }

    private static String chaveEstruturalAte(No[] estrutura, int nivel) {
        No pai = ancestralEstrutural(estrutura, nivel);
        return pai == null ? "" : pai.chave;
    }

    private static No ancestralEstrutural(No[] estrutura, int nivel) {
        for (int i = nivel - 1; i >= 0; i--) {
            if (estrutura[i] != null) return estrutura[i];
        }
        return null;
    }

    private static No ultimoEstrutural(No[] estrutura) {
        for (int i = NIVEIS_ESTRUTURA - 1; i >= 0; i--) {
            if (estrutura[i] != null) return estrutura[i];
        }
        return null;
    }

    private static String prefixo(Tipo t) {
        return switch (t) {
            case PARTE -> "parte";
            case LIVRO -> "liv";
            case TITULO -> "tit";
            case CAPITULO -> "cap";
            case SECAO -> "sec";
            default -> "sub";
        };
    }

    private static String capitalizar(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ montagem

    private static Resultado montar(List<No> nos) {
        List<String> avisos = new ArrayList<>();
        List<Dispositivo> saida = new ArrayList<>(nos.size());
        Map<String, Integer> repeticoes = new HashMap<>();
        Map<No, String> chaveFinal = new HashMap<>();
        int numeroAnterior = -1;

        for (int i = 0; i < nos.size(); i++) {
            No n = nos.get(i);
            // Chave final: se o pai teve a chave alterada por repetição, os filhos herdam o sufixo.
            String chave = n.chave;
            if (n.pai != null && chaveFinal.containsKey(n.pai) && !chaveFinal.get(n.pai).equals(n.pai.chave)) {
                chave = chaveFinal.get(n.pai) + chave.substring(n.pai.chave.length());
            }
            int vez = repeticoes.merge(chave, 1, Integer::sum);
            if (vez > 1) {
                chave = chave + "#" + vez;
                avisos.add("Dispositivo repetido: " + n.identificador + " (ocorr\u00EAncia " + vez
                        + "); verifique se n\u00E3o \u00E9 cita\u00E7\u00E3o ou quebra de linha.");
            }
            chaveFinal.put(n, chave);

            if (n.tipo == Tipo.ARTIGO) {
                int numero = Integer.parseInt(n.chave.substring(4).split("-")[0]);
                if (numero < numeroAnterior) {
                    avisos.add("Numera\u00E7\u00E3o n\u00E3o crescente em " + n.identificador
                            + "; poss\u00EDvel quebra de linha dentro de uma refer\u00EAncia.");
                }
                numeroAnterior = numero;
            }

            String texto = String.join("\n", n.linhas).strip();
            Marca marca = interpretarNotas(texto);
            String pai = n.pai == null ? null : chaveFinal.get(n.pai);
            String caminho = n.pai == null ? n.identificador : caminhoDe(n.pai, nos) + " > " + n.identificador;
            saida.add(new Dispositivo(n.tipo, n.identificador, chave, pai, caminho, i + 1, texto,
                    marca.situacao, marca.alteracao, marca.normaAlteradora));
        }

        if (saida.stream().noneMatch(d -> d.tipo() == Tipo.ARTIGO)) {
            throw new IllegalArgumentException("Texto sem nenhum artigo reconhecido.");
        }
        return new Resultado(List.copyOf(saida), List.copyOf(avisos));
    }

    private static String caminhoDe(No n, List<No> nos) {
        StringBuilder sb = new StringBuilder(n.identificador);
        for (No p = n.pai; p != null; p = p.pai) {
            sb.insert(0, p.identificador + " > ");
        }
        return sb.toString();
    }

    private record Marca(Situacao situacao, TipoAlteracao alteracao, String normaAlteradora) {}

    /** A última nota efetiva do dispositivo vence (ex.: redação dada e depois revogado). */
    private static Marca interpretarNotas(String texto) {
        Matcher m = ANOTACAO.matcher(texto);
        TipoAlteracao alteracao = TipoAlteracao.NENHUMA;
        String norma = null;
        while (m.find()) {
            String nota = m.group(1).toLowerCase(Locale.ROOT);
            TipoAlteracao tipo = nota.startsWith("reda") ? TipoAlteracao.REDACAO_DADA
                    : nota.startsWith("inclu") ? TipoAlteracao.INCLUIDO
                    : nota.startsWith("revog") ? TipoAlteracao.REVOGADO
                    : nota.startsWith("vetad") ? TipoAlteracao.VETADO
                    : TipoAlteracao.RENUMERADO;
            alteracao = tipo;
            Matcher n = NORMA_ALTERADORA.matcher(m.group(2).strip());
            norma = n.find() ? n.group(1).strip() : null;
        }
        Situacao situacao = alteracao == TipoAlteracao.REVOGADO ? Situacao.REVOGADO
                : alteracao == TipoAlteracao.VETADO ? Situacao.VETADO
                : Situacao.UNKNOWN;
        return new Marca(situacao, alteracao, norma);
    }

    private static final class No {
        final Tipo tipo;
        final String identificador;
        final String chave;
        final No pai;
        final List<String> linhas = new ArrayList<>();

        No(Tipo tipo, String identificador, String chave, No pai) {
            this.tipo = tipo;
            this.identificador = identificador;
            this.chave = chave;
            this.pai = pai;
        }
    }
}
