package com.exame.ordem.usuarios;

import org.springframework.modulith.events.Externalized;

/**
 * Evento publicado quando a conta é excluída (RF01.6, R18).
 *
 * <p>{@link Externalized} garante que o Spring Modulith persista o evento
 * em {@code event_publication} mesmo sem listener local no momento. Isso
 * permite que módulos futuros (notificacoes no Mobile 2A, ranking, etc.)
 * o recebam retroativamente ou via reprocessamento.
 */
public record UsuarioExcluido(Long usuarioId) {}