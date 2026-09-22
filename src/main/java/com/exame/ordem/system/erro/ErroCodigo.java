package com.exame.ordem.system.erro;

/**
 * Códigos estáveis de erro. O front confia neles
 * (a mensagem pode mudar, o código não).
 */
public enum ErroCodigo {
    RECURSO_NAO_ENCONTRADO,
    CONFLITO_DE_ESTADO,
    CREDENCIAIS_INVALIDAS,
    REGRA_DE_NEGOCIO,
    LIMITE_EXCEDIDO,
    REQUISICAO_INVALIDA,
    ERRO_INTERNO
}