package com.exame.ordem.usuarios;

/**
 * Recursos gateados por estado de acesso (RF12.5).
 *
 * <p>Simulado NÃO entra aqui: todos os estados têm acesso, mas com limites
 * quantitativos diferentes, expostos por
 * {@link PoliticaAcessoService#limiteDiarioSimulado(Long)}.
 */
public enum RecursoAcesso {
    QUESTAO_AVULSA,
    TUTOR,
    PLANO_ESTUDOS,
    RANKING,
    ANOTACOES
}