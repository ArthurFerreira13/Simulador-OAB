package com.exame.ordem.system.erro.excecoes;

/** HTTP 429 — limite de uso excedido (rate limit, cota diária). */
public class LimiteExcedidoException extends RuntimeException {
    public LimiteExcedidoException(String mensagem) {
        super(mensagem);
    }
}