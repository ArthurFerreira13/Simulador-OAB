package com.exame.ordem.usuarios;

/**
 * Estado de acesso do usuário (RF12.10, RN15, D32).
 *
 * <p>Derivado no servidor a partir de {@code inicioTrial}/{@code fimTrial}
 * e da assinatura (MVP 7). Nunca é gravado como coluna.
 */
public enum EstadoAcesso {
    /** Trial reverso ativo (RF12.8). */
    TRIAL,
    /** Sem trial ativo e sem assinatura. */
    GRATUITO,
    /** Assinatura ativa (a partir do MVP 7). */
    PAGO
}