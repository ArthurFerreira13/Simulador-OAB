package com.exame.ordem.tutor;

/** Erro de negócio do tutor com semântica HTTP própria (ver TutorIaController). */
public class TutorIndisponivelException extends RuntimeException {
    public TutorIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
