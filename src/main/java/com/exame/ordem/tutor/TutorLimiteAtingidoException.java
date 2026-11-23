package com.exame.ordem.tutor;

/** Erro de negócio do tutor com semântica HTTP própria (ver TutorIaController). */
public class TutorLimiteAtingidoException extends RuntimeException {
    public TutorLimiteAtingidoException(String mensagem) {
        super(mensagem);
    }
}
