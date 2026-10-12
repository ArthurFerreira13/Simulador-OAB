package com.exame.ordem.administradores;

import com.exame.ordem.administradores.dto.AdministradorResumo;

/**
 * Porta pública do módulo administradores para autenticação.
 * Consumida apenas pelo módulo auth.
 */
public interface AutenticacaoAdministradorService {

    /**
     * Valida e-mail + senha de administrador.
     * Lança CredenciaisInvalidasException se algo não confere.
     */
    AdministradorResumo autenticar(String email, String senha);
}