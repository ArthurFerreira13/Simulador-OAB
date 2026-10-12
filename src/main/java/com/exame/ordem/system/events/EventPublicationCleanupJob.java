package com.exame.ordem.system.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limpa eventos antigos já entregues da tabela {@code event_publication}
 * do Spring Modulith.
 *
 * <p>A tabela cresce indefinidamente porque o Modulith persiste cada evento
 * publicado para garantir entrega. Sem política de retenção, ela se torna o
 * maior objeto do banco e impacta backup, restauração e VACUUM.
 *
 * <p>Este job remove apenas eventos <b>já entregues</b> com mais de 7 dias.
 * Eventos pendentes ({@code completion_date IS NULL}) nunca são removidos,
 * preservando a garantia de entrega do Modulith.
 */
@Component
class EventPublicationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(EventPublicationCleanupJob.class);
    private final JdbcTemplate jdbc;

    EventPublicationCleanupJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Apaga eventos entregues há mais de 7 dias.
     * Roda todo dia às 3h da manhã.
     */
    @Scheduled(cron = "0 0 3 * * *")
    void limparEventosAntigos() {
        int removidos = jdbc.update("""
            DELETE FROM event_publication
            WHERE completion_date IS NOT NULL
              AND completion_date < NOW() - INTERVAL '7 days'
            """);

        if (removidos > 0) {
            log.info("event_publication: {} linhas antigas removidas", removidos);
        } else {
            log.debug("event_publication: nada a remover");
        }
    }
}