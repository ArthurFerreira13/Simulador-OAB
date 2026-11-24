package com.exame.ordem.legislacao.dto;

/**
 * Situação do dispositivo conforme a fonte. {@link #UNKNOWN} significa que a fonte não permitiu
 * verificar: o tutor NÃO pode apresentar o trecho como vigente.
 */
public enum SituacaoVigencia {
    UNKNOWN, VIGENTE, REVOGADO, VETADO, INCONSTITUCIONAL;

    /** Verdadeiro só quando a fonte afirma a situação (vigente ou revogado, etc.). */
    public boolean verificada() {
        return this != UNKNOWN;
    }
}
