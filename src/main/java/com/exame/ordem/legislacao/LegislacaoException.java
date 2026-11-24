package com.exame.ordem.legislacao;

/** Erro de regra de negócio do módulo (entrada inválida, publicação bloqueada, ingestão concorrente). */
public class LegislacaoException extends RuntimeException {
    public LegislacaoException(String mensagem) {
        super(mensagem);
    }

    public LegislacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
