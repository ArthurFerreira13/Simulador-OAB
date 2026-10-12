package com.exame.ordem.system.erro;

public enum ErroCodigo {
    RECURSO_NAO_ENCONTRADO,
    CONFLITO_DE_ESTADO,
    CREDENCIAIS_INVALIDAS,
    NAO_AUTENTICADO,
    ACESSO_NEGADO,
    REGRA_DE_NEGOCIO,
    LIMITE_EXCEDIDO,          // limite diário de simulado
    RATE_LIMIT_EXCEDIDO,      // rate limit HTTP (login, cadastro)
    REQUISICAO_INVALIDA,
    ERRO_INTERNO
}