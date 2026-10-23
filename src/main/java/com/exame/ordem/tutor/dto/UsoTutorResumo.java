package com.exame.ordem.tutor.dto;

/**
 * Resumo do uso diário do tutor para o usuário autenticado.
 *
 * Expõe as duas métricas de cota (mensagens e tokens) para o cliente
 * mostrar "X de Y hoje" sem ter que adivinhar o limite.
 *
 * Quando o usuário não tem acesso (GRATUITO), todos os limites vêm 0.
 */
public record UsoTutorResumo(
        int mensagensUsadas,
        long tokensUsados,
        int limiteDiario,
        long limiteTokens,
        int restantes
) {}