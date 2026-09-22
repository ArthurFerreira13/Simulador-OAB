package com.exame.ordem.system.erro.excecoes;

/** HTTP 404 — recurso pedido não existe. */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}