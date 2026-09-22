package com.exame.ordem.administradores.service;

import com.exame.ordem.administradores.AdministradorService;
import com.exame.ordem.administradores.dto.CadastroAdministradorRequest;
import com.exame.ordem.administradores.internal.Administrador;
import com.exame.ordem.administradores.internal.AdministradorRepository;
import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class AdministradorServiceImpl implements AdministradorService {

    private final AdministradorRepository repository;
    private final PasswordEncoder passwordEncoder;

    AdministradorServiceImpl(AdministradorRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void cadastrar(CadastroAdministradorRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new ConflitoDeEstadoException("E-mail já cadastrado.");
        }

        String hash = passwordEncoder.encode(request.senha());
        repository.save(Administrador.criar(request.email(), hash, request.nome()));
    }
}