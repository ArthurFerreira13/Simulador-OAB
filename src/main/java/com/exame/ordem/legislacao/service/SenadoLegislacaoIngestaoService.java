package com.exame.ordem.legislacao.service;

import com.exame.ordem.legislacao.dto.FiltroSenadoLegislacao;
import com.exame.ordem.legislacao.dto.ItemIngestaoLegislacao;
import com.exame.ordem.legislacao.dto.ResumoIngestaoLote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SenadoLegislacaoIngestaoService {

    private static final Logger log = LoggerFactory.getLogger(SenadoLegislacaoIngestaoService.class);

    private final SenadoLegislacaoGateway senadoGateway;
    private final LegislacaoLoteIngestaoService loteIngestaoService;
    private final EmbeddingLegislacaoService embeddingLegislacaoService;

    public SenadoLegislacaoIngestaoService(
            SenadoLegislacaoGateway senadoGateway,
            LegislacaoLoteIngestaoService loteIngestaoService,
            EmbeddingLegislacaoService embeddingLegislacaoService
    ) {
        this.senadoGateway = senadoGateway;
        this.loteIngestaoService = loteIngestaoService;
        this.embeddingLegislacaoService = embeddingLegislacaoService;
    }

    public ResumoIngestaoLote ingerirPorFiltro(FiltroSenadoLegislacao filtro, boolean gerarEmbeddingsAgora) {
        List<ItemIngestaoLegislacao> itens = senadoGateway.buscarNormas(filtro);
        ResumoIngestaoLote resumo = loteIngestaoService.ingerirLote(itens);

        if (gerarEmbeddingsAgora && resumo.sucessos() > 0) {
            var resEmb = embeddingLegislacaoService.indexarPendentes();
            log.info("Indexação vetorial concluída após ingestão do Senado: {}", resEmb);
        }
        return resumo;
    }

    public void ingerirPaginasSenado(
            String siglaTipoSenado,
            Integer ano,
            int paginaInicial,
            int paginaFinal,
            boolean indexarNoTutor,
            boolean gerarEmbeddingsAoFinal
    ) {
        for (int pagina = paginaInicial; pagina <= paginaFinal; pagina++) {
            var filtro = FiltroSenadoLegislacao.porPagina(
                    siglaTipoSenado, ano, pagina, indexarNoTutor, java.util.Set.of()
            );
            List<ItemIngestaoLegislacao> itens = senadoGateway.buscarNormas(filtro);
            if (itens.isEmpty()) {
                log.info("Paginação do Senado encerrada na página {} (sem mais resultados).", pagina);
                break;
            }
            ResumoIngestaoLote resumo = loteIngestaoService.ingerirLote(itens);
            log.info("Página {} ({} {}): total={}, sucessos={}, falhas={}",
                    pagina, siglaTipoSenado, ano, resumo.totalProcessadas(), resumo.sucessos(), resumo.falhas());
        }

        if (gerarEmbeddingsAoFinal) {
            var resEmb = embeddingLegislacaoService.indexarPendentes();
            log.info("Indexação vetorial concluída após varredura paginada: {}", resEmb);
        }
    }
}