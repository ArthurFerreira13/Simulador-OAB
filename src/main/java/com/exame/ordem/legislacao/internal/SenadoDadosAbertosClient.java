package com.exame.ordem.legislacao.internal;

import com.exame.ordem.legislacao.dto.DadosNorma;
import com.exame.ordem.legislacao.dto.FiltroSenadoLegislacao;
import com.exame.ordem.legislacao.dto.ItemIngestaoLegislacao;
import com.exame.ordem.legislacao.service.FonteLegislativa;
import com.exame.ordem.legislacao.service.SenadoLegislacaoGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

@Component
class SenadoDadosAbertosClient implements SenadoLegislacaoGateway {

    private static final Logger log = LoggerFactory.getLogger(SenadoDadosAbertosClient.class);
    private static final String LISTA_URL = "https://legis.senado.leg.br/dadosabertos/legislacao/lista.json";
    private static final String DETALHE_URL = "https://legis.senado.leg.br/dadosabertos/legislacao/";

    private final FonteLegislativa fonteLegislativa;
    private final JsonMapper jsonMapper;

    SenadoDadosAbertosClient(FonteLegislativa fonteLegislativa) {
        this.fonteLegislativa = fonteLegislativa;
        this.jsonMapper = JsonMapper.builder().build();
    }

    @Override
    public List<ItemIngestaoLegislacao> buscarNormas(FiltroSenadoLegislacao filtro) {
        String url = montarUrlLista(filtro);
        String chaveCacheLista = montarChaveCacheLista(filtro);

        String jsonBruto = fonteLegislativa.obterConteudo(url, null, chaveCacheLista).conteudo();
        JsonNode root = jsonMapper.readTree(jsonBruto);

        JsonNode listaNode = extrairListaDocumentos(root);
        if (listaNode == null || listaNode.isMissingNode() || listaNode.isNull()) {
            String preview = jsonBruto.length() > 400 ? jsonBruto.substring(0, 400) + "..." : jsonBruto;
            log.warn("Nenhuma norma encontrada no Senado para URL: {} | Resposta: {}", url, preview);
            return fallbackLocalSeDisponivel(filtro, url);
        }

        List<JsonNode> registros = new ArrayList<>();
        if (listaNode.isArray()) {
            listaNode.forEach(registros::add);
        } else if (listaNode.isObject()) {
            registros.add(listaNode);
        }

        if (registros.isEmpty()) {
            return fallbackLocalSeDisponivel(filtro, url);
        }

        List<ItemIngestaoLegislacao> itens = new ArrayList<>(registros.size());
        for (JsonNode normaNode : registros) {
            itens.add(converterParaItem(normaNode, filtro));
        }
        return itens;
    }

    private JsonNode extrairListaDocumentos(JsonNode root) {
        JsonNode listaDoc = root.path("ListaDocumento");
        if (!listaDoc.isMissingNode()) {
            JsonNode docs = listaDoc.path("documentos").path("documento");
            if (!docs.isMissingNode() && !docs.isNull()) {
                return docs;
            }
            JsonNode docsPascal = listaDoc.path("Documentos").path("Documento");
            if (!docsPascal.isMissingNode() && !docsPascal.isNull()) {
                return docsPascal;
            }
        }
        JsonNode genericoDoc = root.findValue("documento");
        if (genericoDoc != null && !genericoDoc.isNull()) {
            return genericoDoc;
        }
        return root.findValue("Documento");
    }

    private List<ItemIngestaoLegislacao> fallbackLocalSeDisponivel(FiltroSenadoLegislacao filtro, String urlConsulta) {
        if (filtro.arquivoFallbackLocal() == null || !Files.exists(filtro.arquivoFallbackLocal())) {
            return List.of();
        }
        log.info("Acionando fallback local {} para filtro {} {}/{}",
                filtro.arquivoFallbackLocal(), filtro.siglaTipoSenado(), filtro.numero(), filtro.ano());

        String especieV25 = mapearEspecieV25(filtro.siglaTipoSenado());
        String numeroNorm = normalizarNumero(filtro.numero());
        String nomeCurto = primeiroNaoVazio(
                filtro.nomeCurtoOverride(),
                (filtro.siglaTipoSenado() != null ? filtro.siglaTipoSenado() + " " : "")
                        + (numeroNorm != null ? numeroNorm + "/" : "")
                        + (filtro.ano() != null ? filtro.ano() : "")
        );

        var dadosNorma = new DadosNorma(
                "FEDERAL",
                especieV25,
                numeroNorm,
                filtro.ano(),
                truncar(nomeCurto, 400),
                truncar(nomeCurto, 160),
                null,
                "Congresso Nacional / Presidência da República",
                null,
                null,
                filtro.indexarNoTutor()
        );

        Set<String> aliases = new LinkedHashSet<>(filtro.aliases());
        if (nomeCurto != null && !nomeCurto.isBlank()) {
            aliases.add(nomeCurto);
        }

        return List.of(new ItemIngestaoLegislacao(
                "LOCAL_" + especieV25 + "_" + (numeroNorm != null ? numeroNorm : "SN") + "_" + (filtro.ano() != null ? filtro.ano() : "0000"),
                null,
                filtro.arquivoFallbackLocal(),
                "FALLBACK_LOCAL",
                urlConsulta,
                "TEXTO",
                false,
                filtro.indexarNoTutor(),
                filtro.forcarReprocessamento(),
                false,
                dadosNorma,
                aliases,
                filtro.disciplinaIds()
        ));
    }

    private ItemIngestaoLegislacao converterParaItem(JsonNode node, FiltroSenadoLegislacao filtro) {
        String codigo = textoDe(node, "id", "codigo", "codigoDocumento", "codigoNorma");
        JsonNode detalheRoot = carregarDetalheJson(codigo);

        String siglaSenado = primeiroNaoVazio(
                textoDe(node, "tipo", "siglaTipo", "siglaTipoDocumento"),
                filtro.siglaTipoSenado()
        );
        String descricaoTipo = textoDe(node, "descricao", "descricaoTipo");
        String especieV25 = mapearEspecieV25(siglaSenado);
        String numeroBruto = primeiroNaoVazio(
                textoDe(node, "numero", "numeroDocumento"),
                filtro.numero()
        );
        String numeroNormalizado = normalizarNumero(numeroBruto);
        Integer ano = parseInteiro(primeiroNaoVazio(
                textoDe(node, "ano", "anoDocumento"),
                filtro.ano() != null ? filtro.ano().toString() : null
        ));

        String tituloPadrao = (descricaoTipo != null ? descricaoTipo : (siglaSenado != null ? siglaSenado : "Norma"))
                + (numeroNormalizado != null ? " nº " + numeroNormalizado : "")
                + (ano != null ? ", de " + ano : "");

        String epigrafe = primeiroNaoVazio(
                textoDe(node, "epigrafe", "descricaoIdentificacao", "titulo"),
                buscarCampoRecursivo(detalheRoot, Set.of("epigrafe", "descricaoidentificacao", "titulo"), v -> true),
                tituloPadrao
        );
        String nomeCurto = primeiroNaoVazio(
                filtro.nomeCurtoOverride(),
                (descricaoTipo != null ? descricaoTipo + " " : (siglaSenado != null ? siglaSenado + " " : ""))
                        + (numeroNormalizado != null ? numeroNormalizado + "/" : "")
                        + (ano != null ? ano : "")
        );
        String ementa = primeiroNaoVazio(
                textoDe(node, "ementa", "textoEmenta", "resumo"),
                buscarCampoRecursivo(detalheRoot, Set.of("ementa", "textoementa", "resumo"), v -> true)
        );
        LocalDate dataAto = parseData(primeiroNaoVazio(
                textoDe(node, "dataAssinatura", "dataNorma", "dataDocumento", "data"),
                buscarCampoRecursivo(detalheRoot, Set.of("dataassinatura", "datanorma", "datadocumento", "data"), v -> v.length() >= 10)
        ));
        LocalDate dataPublicacao = parseData(primeiroNaoVazio(
                textoDe(node, "dataPublicacao", "publicacao"),
                buscarCampoRecursivo(detalheRoot, Set.of("datapublicacao", "publicacao"), v -> v.length() >= 10)
        ));

        List<String> urlsCandidatas = resolverUrlsCandidatas(node, detalheRoot, codigo);
        String urlTexto = urlsCandidatas.isEmpty() ? null : urlsCandidatas.get(0);
        boolean consolidado = urlsCandidatas.stream()
                .anyMatch(u -> u.toLowerCase(Locale.ROOT).contains("compila"));

        String chaveCache = "SENADO_" + especieV25 + "_"
                + (numeroNormalizado != null ? numeroNormalizado : "SN") + "_"
                + (ano != null ? ano : "0000")
                + (codigo != null ? "_" + codigo : "");

        if (fonteLegislativa instanceof FonteLegislativaClient cliente && urlsCandidatas.size() > 1) {
            cliente.registrarUrlsAlternativas(chaveCache, urlsCandidatas.subList(1, urlsCandidatas.size()));
        }

        Set<String> aliases = new LinkedHashSet<>(filtro.aliases());
        if (nomeCurto != null && !nomeCurto.isBlank()) {
            aliases.add(nomeCurto);
        }

        var dadosNorma = new DadosNorma(
                "FEDERAL",
                especieV25,
                numeroNormalizado,
                ano,
                truncar(epigrafe, 400),
                truncar(nomeCurto, 160),
                ementa,
                "Congresso Nacional / Presidência da República",
                dataAto,
                dataPublicacao,
                filtro.indexarNoTutor()
        );

        return new ItemIngestaoLegislacao(
                chaveCache,
                urlTexto,
                filtro.arquivoFallbackLocal(),
                "SENADO_DADOS_ABERTOS",
                urlTexto != null ? urlTexto : (codigo != null ? DETALHE_URL + codigo + ".json" : LISTA_URL),
                "HTML",
                consolidado,
                filtro.indexarNoTutor(),
                filtro.forcarReprocessamento(),
                false,
                dadosNorma,
                aliases,
                filtro.disciplinaIds()
        );
    }

    private JsonNode carregarDetalheJson(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        try {
            String urlDetalhe = DETALHE_URL + encode(codigo) + ".json";
            String jsonDetalhe = fonteLegislativa.obterConteudo(urlDetalhe, null, "SENADO_DETALHE_" + codigo).conteudo();
            return jsonMapper.readTree(jsonDetalhe);
        } catch (Exception e) {
            log.debug("Não foi possível carregar detalhe do código {}: {}", codigo, e.getMessage());
            return null;
        }
    }

    private List<String> resolverUrlsCandidatas(JsonNode node, JsonNode detalheRoot, String codigo) {
        Set<String> urls = new LinkedHashSet<>();

        String diretaNoLista = textoDe(node, "urlTexto", "linkTexto", "urlPublicacao", "url");
        if (diretaNoLista != null && diretaNoLista.startsWith("http")) {
            urls.add(diretaNoLista);
        }

        if (detalheRoot != null) {
            String especifica = buscarCampoRecursivo(
                    detalheRoot,
                    Set.of("urltexto", "linktexto", "urlpublicacao", "textointegral", "urlpaginatexto", "compilacao", "binario"),
                    v -> v.startsWith("http") || v.startsWith("/")
            );
            if (especifica != null) {
                urls.add(especifica.startsWith("/") ? "https://legis.senado.leg.br" + especifica : especifica);
            }

            String generica = buscarCampoRecursivo(
                    detalheRoot,
                    Set.of("url", "link"),
                    v -> v.startsWith("http")
                            && !v.contains("/dadosabertos/")
                            && (v.contains("/norma/") || v.contains("publicacao") || v.contains("texto") || v.contains("planalto"))
            );
            if (generica != null) {
                urls.add(generica);
            }
        }

        if (codigo != null && !codigo.isBlank()) {
            urls.add("https://legis.senado.leg.br/norma/" + codigo);
        }

        return List.copyOf(urls);
    }

    private String buscarCampoRecursivo(JsonNode node, Set<String> nomesLower, Predicate<String> filtroValor) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            for (var entry : node.properties()) {
                if (nomesLower.contains(entry.getKey().toLowerCase()) && entry.getValue().isValueNode()) {
                    String val = entry.getValue().asString();
                    if (val != null && !val.isBlank() && filtroValor.test(val.trim())) {
                        return val.trim();
                    }
                }
                String nested = buscarCampoRecursivo(entry.getValue(), nomesLower, filtroValor);
                if (nested != null) return nested;
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String nested = buscarCampoRecursivo(item, nomesLower, filtroValor);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private String montarUrlLista(FiltroSenadoLegislacao f) {
        List<String> params = new ArrayList<>();
        if (f.siglaTipoSenado() != null && !f.siglaTipoSenado().isBlank()) {
            params.add("tipo=" + encode(f.siglaTipoSenado().trim().toUpperCase()));
        }
        if (f.numero() != null && !f.numero().isBlank()) {
            params.add("numero=" + encode(f.numero().trim()));
        }
        if (f.ano() != null) {
            params.add("ano=" + f.ano());
        }
        if (f.pagina() != null && f.pagina() > 0) {
            params.add("pagina=" + f.pagina());
        }
        return params.isEmpty() ? LISTA_URL : LISTA_URL + "?" + String.join("&", params);
    }

    private String montarChaveCacheLista(FiltroSenadoLegislacao f) {
        return "SENADO_LISTA_"
                + (f.siglaTipoSenado() != null ? f.siglaTipoSenado() : "TODOS") + "_"
                + (f.numero() != null ? f.numero() : "ALL") + "_"
                + (f.ano() != null ? f.ano() : "ALL") + "_P"
                + (f.pagina() != null ? f.pagina() : 1);
    }

    private String mapearEspecieV25(String sigla) {
        if (sigla == null) return "OUTRA";
        String norm = sigla.trim().toUpperCase();
        if (norm.startsWith("LEI-")) {
            return "LEI";
        }
        return switch (norm) {
            case "CON", "CF" -> "CONSTITUICAO";
            case "EMC" -> "EMENDA_CONSTITUCIONAL";
            case "LEI" -> "LEI";
            case "LCP" -> "LEI_COMPLEMENTAR";
            case "LDL" -> "LEI_DELEGADA";
            case "DEC" -> "DECRETO";
            case "DEL" -> "DECRETO_LEI";
            case "MPV" -> "MEDIDA_PROVISORIA";
            case "RSF", "RCN" -> "RESOLUCAO";
            default -> "OUTRA";
        };
    }

    private String normalizarNumero(String numero) {
        if (numero == null || numero.isBlank()) return null;
        String semZeros = numero.trim().replaceFirst("^0+(?!$)", "");
        return truncar(semZeros, 40);
    }

    private Integer parseInteiro(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseData(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return LocalDate.parse(valor.trim().substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private String textoDe(JsonNode node, String... candidatos) {
        if (node == null || !node.isObject()) return null;
        for (String candidato : candidatos) {
            JsonNode filho = node.get(candidato);
            if (filho != null && !filho.isNull() && filho.isValueNode()) {
                String txt = filho.asString();
                if (txt != null && !txt.isBlank()) return txt.trim();
            }
        }
        for (var entry : node.properties()) {
            for (String candidato : candidatos) {
                if (entry.getKey().equalsIgnoreCase(candidato) && entry.getValue().isValueNode()) {
                    String txt = entry.getValue().asString();
                    if (txt != null && !txt.isBlank()) return txt.trim();
                }
            }
        }
        return null;
    }

    private String primeiroNaoVazio(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}