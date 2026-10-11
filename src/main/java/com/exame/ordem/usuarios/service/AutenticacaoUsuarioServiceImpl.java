package com.exame.ordem.usuarios.service;

import com.exame.ordem.system.erro.excecoes.CredenciaisInvalidasException;
import com.exame.ordem.usuarios.AutenticacaoUsuarioService;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import com.exame.ordem.usuarios.internal.ProvedorAuth;
import com.exame.ordem.usuarios.internal.Usuario;
import com.exame.ordem.usuarios.internal.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AutenticacaoUsuarioServiceImpl implements AutenticacaoUsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    AutenticacaoUsuarioServiceImpl(UsuarioRepository usuarioRepository,
                                   PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResumo autenticarLocal(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new CredenciaisInvalidasException("Credenciais inválidas."));

        if (usuario.getProvedorAuth() != ProvedorAuth.LOCAL) {
            throw new CredenciaisInvalidasException("Credenciais inválidas.");
        }

        if (usuario.getSenhaHash() == null
                || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException("Credenciais inválidas.");
        }

        return toResumo(usuario);
    }

    private UsuarioResumo toResumo(Usuario usuario) {
        return new UsuarioResumo(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNome(),
                usuario.getDataProvaAlvo(),
                usuario.getCriadoEm());
    }
}