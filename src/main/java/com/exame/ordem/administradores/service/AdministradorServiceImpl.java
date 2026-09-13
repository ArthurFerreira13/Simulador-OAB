package com.exame.ordem.administradores.service;

import com.exame.ordem.administradores.AdministradorService;
import com.exame.ordem.administradores.dto.CadastroAdministradorRequest;
import com.exame.ordem.administradores.internal.Administrador;
import com.exame.ordem.administradores.internal.AdministradorRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class AdministradorServiceImpl implements AdministradorService {

    private final AdministradorRepository repository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    AdministradorServiceImpl(AdministradorRepository repository) {
        this.repository = repository;
    }

    @Override
    public void cadastrar(CadastroAdministradorRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new IllegalStateException("E-mail já cadastrado.");
        }

        String hash = passwordEncoder.encode(request.senha());
        repository.save(Administrador.criar(request.email(), hash, request.nome()));
    }
}