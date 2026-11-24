package com.exame.ordem.legislacao.dto;

import java.time.LocalDate;

/**
 * @param pergunta       texto do aluno (obrigatório, até 1000 caracteres)
 * @param dataReferencia {@code null} = versão atual publicada. Data passada ainda não é suportada
 *                       (a Fase A não tem vigência histórica verificada) e devolve "sem base".
 * @param limite         de 1 a 20 trechos
 * @param disciplinaId   restringe às normas associadas à disciplina; {@code null} = todas
 */
public record ContextoConsultaLegislacao(
        String pergunta,
        LocalDate dataReferencia,
        int limite,
        Long disciplinaId
) {
    public ContextoConsultaLegislacao {
        if (pergunta == null || pergunta.isBlank()) {
            throw new IllegalArgumentException("pergunta é obrigatória");
        }
        if (pergunta.length() > 1000) {
            throw new IllegalArgumentException("pergunta excede 1000 caracteres");
        }
        if (limite < 1 || limite > 20) {
            throw new IllegalArgumentException("limite deve estar entre 1 e 20");
        }
    }
}
