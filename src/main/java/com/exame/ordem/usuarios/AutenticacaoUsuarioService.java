package com.exame.ordem.usuarios;

import com.exame.ordem.usuarios.dto.UsuarioResumo;

/**
 * Porta pública do módulo usuarios para autenticação.
 *
 * Separada de UsuarioService porque autenticação é uma responsabilidade
 * diferente de CRUD de conta. Consumida apenas pelo módulo `auth`.
 *
 * Hoje só o fluxo LOCAL (email + senha) está implementado.
 * Quando OAuth entrar (GOOGLE, FACEBOOK, EMAIL_LINK), esta porta
 * ganha novos métodos específicos — não substitui o atual.
 */
public interface AutenticacaoUsuarioService {

    /**
     * Autenticação LOCAL: valida e-mail + senha.
     * Lança CredenciaisInvalidasException se:
     *  - o e-mail não existe
     *  - o provedor não é LOCAL
     *  - a senha não confere
     */
    UsuarioResumo autenticarLocal(String email, String senha);
}