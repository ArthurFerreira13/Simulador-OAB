package com.exame.ordem.legislacao.service;

import com.exame.ordem.legislacao.config.LegislacaoIngestaoProperties;
import com.exame.ordem.legislacao.internal.LegislacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Libera normas travadas por um job que ficou "em andamento" porque o processo caiu no meio da
 * ingestão. Sem isto, o índice único de job em andamento bloqueia novas ingestões da norma para sempre.
 *
 * <p>O limite ({@code app.legislacao.ingestao.job-timeout}, padrão 15 min) precisa ser maior que a
 * ingestão mais longa; com várias instâncias, um job vivo de outra instância só é encerrado se passar
 * desse tempo sem atividade.
 */
@Component
class LegislacaoJobReaper {

    private static final Logger log = LoggerFactory.getLogger(LegislacaoJobReaper.class);

    private final LegislacaoRepository repo;
    private final LegislacaoIngestaoProperties props;

    LegislacaoJobReaper(LegislacaoRepository repo, LegislacaoIngestaoProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    void aoIniciar() {
        varrer();
    }

    @Scheduled(fixedDelayString = "${app.legislacao.ingestao.reaper-intervalo:PT5M}", initialDelayString = "PT5M")
    void varrer() {
        try {
            int encerrados = repo.encerrarJobsOrfaos(props.jobTimeout());
            if (encerrados > 0) {
                log.warn("{} job(s) de ingestão sem atividade há mais de {} foram encerrados como FAILED.",
                        encerrados, props.jobTimeout());
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao varrer jobs órfãos de ingestão: {}", e.getMessage());
        }
    }
}