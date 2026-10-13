package com.exame.ordem.usuarios.dto;

import com.exame.ordem.usuarios.EstadoAcesso;
import com.exame.ordem.usuarios.RecursoAcesso;

import java.util.Set;

/**
 * Resumo do estado de acesso (RF12.11, Doc 04 §7).
 *
 * @param diasRestantesTrial   dias restantes; {@code null} fora de TRIAL
 * @param limiteDiarioSimulado 0 = sem limite; N = N simulados por dia
 */
public record AcessoResumo(
        EstadoAcesso estado,
        Long diasRestantesTrial,
        int limiteDiarioSimulado,
        Set<RecursoAcesso> recursosPermitidos
) {}