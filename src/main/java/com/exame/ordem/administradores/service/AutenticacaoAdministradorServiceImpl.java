package com.exame.ordem.administradores.service;

import com.exame.ordem.administradores.AutenticacaoAdministradorService;
import com.exame.ordem.administradores.dto.AdministradorResumo;
import com.exame.ordem.administradores.internal.Administrador;
import com.exame.ordem.administradores.internal.AdministradorRepository;
import com.exame.ordem.system.erro.excecoes.CredenciaisInvalidasException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AutenticacaoAdministradorServiceImpl implements AutenticacaoAdministradorService {

    private final AdministradorRepository repository;
    private final PasswordEncoder passwordEncoder;

    AutenticacaoAdministradorServiceImpl(AdministradorRepository repository,
                                         PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public AdministradorResumo autenticar(String email, String senha) {
        Administrador admin = repository.findByEmail(email)
                .orElseThrow(() -> new CredenciaisInvalidasException("Credenciais inválidas."));

        if (!passwordEncoder.matches(senha, admin.getSenhaHash())) {
            throw new CredenciaisInvalidasException("Credenciais inválidas.");
        }

        return new AdministradorResumo(
                admin.getId(),
                admin.getEmail(),
                admin.getNome(),
                admin.getCriadoEm());
    }
}