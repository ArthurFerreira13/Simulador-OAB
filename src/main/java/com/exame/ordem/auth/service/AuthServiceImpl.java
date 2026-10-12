package com.exame.ordem.auth.service;

import com.exame.ordem.administradores.AutenticacaoAdministradorService;
import com.exame.ordem.administradores.dto.AdministradorResumo;
import com.exame.ordem.auth.AuthService;
import com.exame.ordem.auth.dto.LoginRequest;
import com.exame.ordem.auth.dto.TokenResponse;
import com.exame.ordem.auth.internal.JwtService;
import com.exame.ordem.usuarios.AutenticacaoUsuarioService;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import org.springframework.stereotype.Service;

@Service
class AuthServiceImpl implements AuthService {

    private final AutenticacaoUsuarioService autenticacaoUsuarioService;
    private final AutenticacaoAdministradorService autenticacaoAdministradorService;
    private final JwtService jwtService;

    AuthServiceImpl(AutenticacaoUsuarioService autenticacaoUsuarioService,
                    AutenticacaoAdministradorService autenticacaoAdministradorService,
                    JwtService jwtService) {
        this.autenticacaoUsuarioService = autenticacaoUsuarioService;
        this.autenticacaoAdministradorService = autenticacaoAdministradorService;
        this.jwtService = jwtService;
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        UsuarioResumo usuario = autenticacaoUsuarioService.autenticarLocal(
                request.email(), request.senha());
        return jwtService.gerar(usuario);
    }

    @Override
    public TokenResponse loginAdmin(LoginRequest request) {
        AdministradorResumo admin = autenticacaoAdministradorService.autenticar(
                request.email(), request.senha());
        return jwtService.gerar(admin);
    }
}