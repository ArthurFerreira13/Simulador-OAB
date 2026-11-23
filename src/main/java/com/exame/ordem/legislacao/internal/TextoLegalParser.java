package com.exame.ordem.legislacao.internal;

import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai artigos de um texto legal já normalizado ({@link TextoNormalizador}).
 *
 * <p>Reconhece "Art. 1º", "Art. 1°", "Art. 1o", "Art. 10.", "Art. 1.000" e "Art. 121-A" no
 * INÍCIO de linha (citações entre aspas em leis alteradoras não casam). Preserva parágrafos,
 * incisos e alíneas dentro do texto do artigo e mantém notas de revogação.
 */
public final class TextoLegalParser {

    // "o" só vale como ordinal quando não é início de palavra ("Art. 4 ordem" não perde o "o").
    private static final Pattern CABECALHO = Pattern.compile(
            "(?m)^[ \\t]*Art\\.?[ \\t]*(\\d+(?:\\.\\d{3})*)[ \\t]*(?:\\u00BA|\\u00B0|o(?!\\p{L}))?"
                    + "[ \\t]*(?:-[ \\t]*([A-Z])\\b)?");

    private static final Pattern REVOGADO =
            Pattern.compile("(?is)\\W*\\(?\\s*Revogad[oa]");

    private TextoLegalParser() {}

    public record ArtigoNormalizado(String identificador, String texto, int ordem, boolean revogado) {}

    public record ResultadoParse(List<ArtigoNormalizado> artigos, List<String> avisos) {}

    private record Marca(int inicio, int fimCabecalho, String identificador, int numero) {}

    public static ResultadoParse extrairArtigos(String texto) {
        List<Marca> marcas = new ArrayList<>();
        Matcher m = CABECALHO.matcher(texto);
        while (m.find()) {
            String numero = m.group(1);
            String letra = m.group(2);
            String identificador = "Art. " + numero + (letra != null ? "-" + letra : "");
            int base = Integer.parseInt(numero.replace(".", ""));
            marcas.add(new Marca(m.start(), m.end(), identificador, base));
        }

        if (marcas.isEmpty()) {
            throw new RegraDeNegocioException("Texto sem nenhum artigo reconhecido.");
        }

        List<String> avisos = new ArrayList<>();
        List<ArtigoNormalizado> artigos = new ArrayList<>(marcas.size());
        Map<String, Integer> ocorrencias = new HashMap<>();
        int numeroAnterior = -1;

        for (int i = 0; i < marcas.size(); i++) {
            Marca atual = marcas.get(i);
            int fim = (i + 1 < marcas.size()) ? marcas.get(i + 1).inicio() : texto.length();

            String conteudo = texto.substring(atual.inicio(), fim).strip();
            String corpo = texto.substring(atual.fimCabecalho(), fim);
            boolean revogado = REVOGADO.matcher(corpo).lookingAt();

            String identificador = atual.identificador();
            int vez = ocorrencias.merge(identificador, 1, Integer::sum);
            if (vez > 1) {
                identificador = identificador + " [" + vez + "]";
                avisos.add("Identificador repetido: " + atual.identificador()
                        + " (ocorrência " + vez + "); verifique se não é uma citação.");
            }

            if (atual.numero() < numeroAnterior) {
                avisos.add("Numeração não crescente em " + atual.identificador()
                        + "; possível quebra de linha dentro de uma referência.");
            }
            numeroAnterior = atual.numero();

            artigos.add(new ArtigoNormalizado(identificador, conteudo, i + 1, revogado));
        }

        return new ResultadoParse(List.copyOf(artigos), List.copyOf(avisos));
    }
}
