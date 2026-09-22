package com.exame.ordem.auth.service;

import com.exame.ordem.auth.AuthService;
import com.exame.ordem.auth.dto.LoginRequest;
import com.exame.ordem.auth.dto.TokenResponse;
import com.exame.ordem.auth.internal.JwtService;
import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import org.springframework.stereotype.Service;

@Service
class AuthServiceImpl implements AuthService {

    private final UsuarioService usuarioService;
    private final JwtService jwtService;

    AuthServiceImpl(UsuarioService usuarioService, JwtService jwtService) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        // Reaproveita a autenticação existente (email+senha → BCrypt)
        UsuarioResumo usuario = usuarioService.consultarPerfil(request.email(), request.senha());
        return jwtService.gerar(usuario);
    }
}