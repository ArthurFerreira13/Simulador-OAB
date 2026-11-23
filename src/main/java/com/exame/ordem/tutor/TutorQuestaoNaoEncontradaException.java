package com.exame.ordem.tutor;

/** Erro de negócio do tutor com semântica HTTP própria (ver TutorIaController). */
public class TutorQuestaoNaoEncontradaException extends RuntimeException {
    public TutorQuestaoNaoEncontradaException(String mensagem) {
        super(mensagem);
    }
}
