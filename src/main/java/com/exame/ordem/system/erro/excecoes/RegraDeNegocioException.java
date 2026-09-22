package com.exame.ordem.system.erro.excecoes;

/**
 * HTTP 422 — regra de negócio violada.
 * Ex.: não é possível responder questão anulada.
 */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}