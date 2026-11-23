package com.exame.ordem.tutor;

/** Erro de negócio do tutor com semântica HTTP própria (ver TutorIaController). */
public class TutorAcessoNegadoException extends RuntimeException {
    public TutorAcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
