package com.exame.ordem.usuarios.internal;

import com.exame.ordem.usuarios.UsuarioExcluido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listener que garante a persistência do {@link UsuarioExcluido} em
 * {@code event_publication}.
 *
 * <p>Sem um listener, o Modulith publica o evento apenas em memória e não
 * persiste. Este listener só loga, mas cumpre o papel de "assinante" que
 * ativa a persistência garantida pelo ADR-010. Quando um módulo de verdade
 * consumir o evento (notificacoes no Mobile 2A, ranking, etc.), este
 * listener pode ser mantido para observabilidade ou removido.
 */
@Component
class UsuarioEventLogger {

    private static final Logger log = LoggerFactory.getLogger(UsuarioEventLogger.class);

    @ApplicationModuleListener
    void on(UsuarioExcluido evento) {
        log.info("UsuarioExcluido processado: usuarioId={}", evento.usuarioId());
    }
}