package com.exame.ordem.legislacao.internal;

import com.exame.ordem.legislacao.internal.ParserCompilacaoCamara.Dispositivo;
import com.exame.ordem.legislacao.internal.ParserCompilacaoCamara.Tipo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ParserCompilacaoCamaraTest {

    private static final String REVOGADO_13146 =
            "(Revogado pela Lei n\u00BA 13.146, de 6/7/2015, publicada no DOU de 7/7/2015, em vigor 180 dias ap\u00F3s a publica\u00E7\u00E3o)";

    /** Monta um par\u00E1grafo no mesmo formato do HTML da Compila\u00E7\u00E3o Monovigente. */
    private static String p(String texto, String anotacao) {
        return "<p style=\"text-indent:56.7pt; text-align:justify; font-size:1.2em\"><span>" + texto + "</span>"
                + (anotacao == null ? "" :
                "<a href=\"http://www2.camara.leg.br/x\" style=\"text-decoration:none\">"
                        + "<span class=\"Hyperlink\" style=\"font-style:italic\">" + anotacao + "</span></a>")
                + "</p>";
    }

    private static final String HTML = "<html><body>"
            + p("LIVRO I", null)
            + p("DAS PESSOAS", null)
            + p("Art. 1\u00BA Toda pessoa \u00E9 capaz de direitos e deveres na ordem civil.", null)
            + p("Art. 3\u00BA S\u00E3o absolutamente incapazes de exercer pessoalmente os atos da vida civil os menores de 16 (dezesseis) anos. ",
            "(Artigo com reda\u00E7\u00E3o dada pela Lei n\u00BA 13.146, de 6/7/2015)")
            + p("I - ", REVOGADO_13146)
            + p("II - ", REVOGADO_13146)
            + p("III - ", REVOGADO_13146)
            + p("Art. 4\u00BA S\u00E3o incapazes, relativamente a certos atos ou \u00E0 maneira de os exercer:", null)
            + p("I - os maiores de dezesseis e menores de dezoito anos;", null)
            + p("Par\u00E1grafo \u00FAnico. A capacidade dos ind\u00EDgenas ser\u00E1 regulada por legisla\u00E7\u00E3o especial.",
            "(Reda\u00E7\u00E3o dada pela Lei n\u00BA 13.146, de 6/7/2015)")
            + p("Bras\u00EDlia, 10 de janeiro de 2002; 181\u00BA da Independ\u00EAncia e 114\u00BA da Rep\u00FAblica.", null)
            + p("FERNANDO HENRIQUE CARDOSO", null)
            + "</body></html>";

    @Test
    void reconheceArtigosIncisosAnotacoesEHierarquia() {
        var r = ParserCompilacaoCamara.parse(HTML);
        Map<String, Dispositivo> porCaminho = r.dispositivos().stream()
                .collect(Collectors.toMap(Dispositivo::caminho, Function.identity()));

        assertThat(porCaminho).containsKeys("art1", "art3", "art3_cpt_inc1", "art3_cpt_inc2",
                "art3_cpt_inc3", "art4", "art4_cpt_inc1", "art4_paru");

        assertThat(porCaminho.get("art1").hierarquia()).containsExactly("LIVRO I - DAS PESSOAS");

        // caput do art. 3\u00BA: vigente, com a anota\u00E7\u00E3o separada do texto
        assertThat(porCaminho.get("art3").revogado()).isFalse();
        assertThat(porCaminho.get("art3").anotacoes()).hasSize(1);
        assertThat(porCaminho.get("art3").texto()).endsWith("anos.");

        // incisos I a III: revogados
        assertThat(porCaminho.get("art3_cpt_inc1").revogado()).isTrue();
        assertThat(porCaminho.get("art3_cpt_inc3").revogado()).isTrue();
        assertThat(porCaminho.get("art3_cpt_inc1").texto()).isEmpty();

        // art. 4\u00BA: inciso vigente e par\u00E1grafo \u00FAnico
        assertThat(porCaminho.get("art4_cpt_inc1").revogado()).isFalse();
        assertThat(porCaminho.get("art4_cpt_inc1").texto())
                .isEqualTo("os maiores de dezesseis e menores de dezoito anos;");
        assertThat(porCaminho.get("art4_paru").tipo()).isEqualTo(Tipo.PARAGRAFO);
        assertThat(porCaminho.get("art4_paru").texto()).startsWith("A capacidade dos ind");

        // o fecho ("Bras\u00EDlia, ...") encerra o parsing; a assinatura n\u00E3o vira dispositivo
        assertThat(r.dispositivos()).noneMatch(d -> d.texto().contains("FERNANDO"));
        assertThat(r.naoReconhecidos()).isEmpty();
    }

    /**
     * Resumo do C\u00F3digo Civil real. Roda s\u00F3 se o HTML estiver salvo (veja o caminho abaixo); n\u00E3o faz parte do CI.
     * Salvar com: curl.exe -s -o "$env:TEMP\\cc-texto-2.html" "https://normas.leg.br/api/public/binario/{id}/texto"
     */
    @Test
    void resumoDoCodigoCivilReal() throws Exception {
        Path arquivo = Path.of(System.getProperty("java.io.tmpdir"), "cc-texto-2.html");
        Assumptions.assumeTrue(Files.exists(arquivo), "HTML n\u00E3o encontrado em " + arquivo);

        var r = ParserCompilacaoCamara.parse(Files.readString(arquivo, StandardCharsets.UTF_8));
        var ds = r.dispositivos();

        long artigos = ds.stream().filter(d -> d.tipo() == Tipo.ARTIGO).count();
        long revogados = ds.stream().filter(Dispositivo::revogado).count();

        System.out.println("=== RESUMO C\u00D3DIGO CIVIL ===");
        System.out.println("dispositivos: " + ds.size() + " | artigos: " + artigos + " | revogados: " + revogados);
        System.out.println("tem art2046? " + ds.stream().anyMatch(d -> d.caminho().equals("art2046")));
        System.out.println("--- art3 e filhos ---");
        ds.stream().filter(d -> d.caminho().startsWith("art3_") || d.caminho().equals("art3"))
                .forEach(d -> System.out.println(d.caminho() + " | revogado=" + d.revogado() + " | " + d.texto()));
        System.out.println("--- hierarquia do art1 ---");
        ds.stream().filter(d -> d.caminho().equals("art1")).findFirst()
                .ifPresent(d -> System.out.println(d.hierarquia()));
        System.out.println("--- n\u00E3o reconhecidos: " + r.naoReconhecidos().size() + " (primeiros 20) ---");
        r.naoReconhecidos().stream().limit(20).forEach(System.out::println);

        assertThat(artigos).isGreaterThan(1500);
    }
}