package com.exame.ordem.system.erro.excecoes;

/**
 * HTTP 409 — operação válida, mas conflita com o estado atual do recurso.
 * Ex.: tentar abandonar sessão já finalizada.
 */
public class ConflitoDeEstadoException extends RuntimeException {
    public ConflitoDeEstadoException(String mensagem) {
        super(mensagem);
    }
}