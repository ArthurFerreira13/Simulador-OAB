package com.exame.ordem.legislacao.internal;

import com.exame.ordem.legislacao.service.PreparadorConteudo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
class PreparadorConteudoNorma implements PreparadorConteudo {

    @Override
    public String preparar(String bruto, String formatoEntrada) {
        if (bruto == null || bruto.isBlank()) {
            throw new IllegalArgumentException("Conteúdo da norma veio vazio.");
        }
        if ("HTML".equalsIgnoreCase(formatoEntrada)) {
            Document doc = Jsoup.parse(bruto);
            doc.select("strike, s, del, script, style, noscript, nav, header, footer, aside, form, button, #barra-brasil, .barra-brasil, .breadcrumb, .navbar, .menu, .rodape, .footer").remove();

            Element body = doc.body();
            body.select("br").after("\n");
            body.select("p, div, h1, h2, h3, h4, h5, h6, li, tr, blockquote").before("\n").after("\n");

            return body.wholeText()
                    .replace('\u00A0', ' ')
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .replaceAll("[ \\t\\x0B\\f]+", " ")
                    .replaceAll(" *\\n *", "\n")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();
        }
        return bruto.replace("\r\n", "\n").trim();
    }
}