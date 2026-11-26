package com.exame.ordem.legislacao.service;

import com.exame.ordem.legislacao.dto.ItemIngestaoLegislacao;
import com.exame.ordem.legislacao.dto.ResumoIngestaoLote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LegislacaoLoteIngestaoService {

    private static final Logger log = LoggerFactory.getLogger(LegislacaoLoteIngestaoService.class);

    private final FonteLegislativa fonteLegislativa;
    private final PreparadorConteudo preparadorConteudo;
    private final IngestaoLegislacaoService ingestaoLegislacaoService;

    public LegislacaoLoteIngestaoService(
            FonteLegislativa fonteLegislativa,
            PreparadorConteudo preparadorConteudo,
            IngestaoLegislacaoService ingestaoLegislacaoService
    ) {
        this.fonteLegislativa = fonteLegislativa;
        this.preparadorConteudo = preparadorConteudo;
        this.ingestaoLegislacaoService = ingestaoLegislacaoService;
    }

    public ResumoIngestaoLote ingerirLote(List<ItemIngestaoLegislacao> itens) {
        List<Object> resultados = new ArrayList<>();
        int sucessos = 0;
        int falhas = 0;

        for (ItemIngestaoLegislacao item : itens) {
            try {
                var coleta = fonteLegislativa.obterConteudo(
                        item.urlApiExterna(),
                        item.arquivoFallbackLocal(),
                        item.chaveCache()
                );
                String textoPreparado = preparadorConteudo.preparar(coleta.conteudo(), item.formatoEntrada());
                String urlFonte = (coleta.urlEfetiva() != null && !coleta.urlEfetiva().isBlank())
                        ? coleta.urlEfetiva()
                        : item.urlReferencia();

                var comando = IngestaoLegislacaoService.ComandoIngestao.builder()
                        .norma(item.dadosNorma())
                        .aliases(item.palavrasChave())
                        .disciplinaIds(item.temas())
                        .fonte(item.origem())
                        .url(urlFonte)
                        .formato("TEXTO")
                        .consolidado(item.consolidado())
                        .texto(textoPreparado)
                        .forcarReprocessamento(item.forcarReprocessamento())
                        .forcarPublicacao(item.forcarPublicacao())
                        .build();

                resultados.add(ingestaoLegislacaoService.ingerir(comando));
                sucessos++;
            } catch (Exception e) {
                falhas++;
                log.error("Falha ao ingerir {} ({}): {}",
                        item.dadosNorma().nomeCurto(),
                        item.chaveCache(),
                        e.getMessage(), e);
            }
        }

        return new ResumoIngestaoLote(itens.size(), sucessos, falhas, resultados);
    }
}