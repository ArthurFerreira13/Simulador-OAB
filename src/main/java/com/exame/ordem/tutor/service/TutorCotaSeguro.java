package com.exame.ordem.tutor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
class TutorCotaSeguro {
    private static final Logger log = LoggerFactory.getLogger(TutorCotaSeguro.class);
    private final CotaTutorTransacionalService cotaService;
    private final Clock clock;

    TutorCotaSeguro(CotaTutorTransacionalService cotaService, Clock clock) {
        this.cotaService = cotaService;
        this.clock = clock;
    }

    void estornar(Long usuarioId, LocalDate data) {
        try {
            cotaService.estornar(usuarioId, data, LocalDateTime.now(clock));
        } catch (Exception e) {
            log.warn("Falha ao estornar cota do usuario={} em {}: {}", usuarioId, data, e.getMessage());
        }
    }

    void somarTokens(Long usuarioId, LocalDate data, int tokensConsumidos) {
        try {
            cotaService.somarTokens(usuarioId, data, tokensConsumidos, LocalDateTime.now(clock));
        } catch (Exception e) {
            log.warn("Falha ao contabilizar {} tokens para usuario={}: {}",
                    tokensConsumidos, usuarioId, e.getMessage());
        }
    }
}