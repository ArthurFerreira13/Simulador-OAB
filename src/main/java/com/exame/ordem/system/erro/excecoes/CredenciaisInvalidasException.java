package com.exame.ordem.system.erro.excecoes;

/** HTTP 401 — email/senha (ou token) não conferem. */
public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException(String mensagem) {
        super(mensagem != null ? mensagem : "Credenciais inválidas.");
    }
}