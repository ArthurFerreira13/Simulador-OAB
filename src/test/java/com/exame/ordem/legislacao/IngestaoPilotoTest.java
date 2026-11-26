package com.exame.ordem.legislacao;

import com.exame.ordem.legislacao.dto.FiltroSenadoLegislacao;
import com.exame.ordem.legislacao.service.SenadoLegislacaoIngestaoService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.util.Set;

@SpringBootTest
@ActiveProfiles("dev")
class IngestaoPilotoTest {

    private static final Logger log = LoggerFactory.getLogger(IngestaoPilotoTest.class);

    @Autowired
    SenadoLegislacaoIngestaoService senadoIngestaoService;

    @Test
    void deveIngerirNormasViaApiListaJsonDoSenado() {
        var lindb = FiltroSenadoLegislacao.normaEspecifica(
                "DEL",
                "4657",
                1942,
                "LINDB",
                Path.of("src/test/resources/legislacao/lindb.txt"),
                Set.of("lindb", "lei de introducao", "decreto-lei 4657/1942"),
                Set.of()
        );

        var resumoLindb = senadoIngestaoService.ingerirPorFiltro(lindb, false);
        log.info("Ingestão LINDB via Senado lista.json: {}", resumoLindb);

        var estatutoOab = FiltroSenadoLegislacao.normaEspecifica(
                "LEI",
                "8906",
                1994,
                "Estatuto da OAB",
                null,
                Set.of("estatuto da oab", "estatuto da advocacia", "lei 8906/1994"),
                Set.of()
        );

        var resumoOab = senadoIngestaoService.ingerirPorFiltro(estatutoOab, false);
        log.info("Ingestão Estatuto OAB via Senado lista.json: {}", resumoOab);
    }
}