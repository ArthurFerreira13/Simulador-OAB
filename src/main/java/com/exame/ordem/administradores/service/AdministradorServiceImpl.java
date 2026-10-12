package com.exame.ordem.administradores.service;

import com.exame.ordem.administradores.AdministradorService;
import com.exame.ordem.administradores.dto.CadastroAdministradorRequest;
import com.exame.ordem.administradores.internal.Administrador;
import com.exame.ordem.administradores.internal.AdministradorRepository;
import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
class AdministradorServiceImpl implements AdministradorService {

    private final AdministradorRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String chaveCadastro;

    AdministradorServiceImpl(AdministradorRepository repository,
                             PasswordEncoder passwordEncoder,
                             @Value("${app.admin.chave-cadastro}") String chaveCadastro) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.chaveCadastro = chaveCadastro;
    }

    @Override
    @Transactional
    public void cadastrar(CadastroAdministradorRequest request) {
        if (!Objects.equals(chaveCadastro, request.chave())) {
            throw new RegraDeNegocioException("Chave de cadastro inválida.");
        }

        if (repository.existsByEmail(request.email())) {
            throw new ConflitoDeEstadoException("E-mail já cadastrado.");
        }

        String hash = passwordEncoder.encode(request.senha());
        repository.save(Administrador.criar(request.email(), hash, request.nome()));
    }
}