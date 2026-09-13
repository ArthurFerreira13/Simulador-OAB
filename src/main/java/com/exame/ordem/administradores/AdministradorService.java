package com.exame.ordem.administradores;

import com.exame.ordem.administradores.dto.CadastroAdministradorRequest;

public interface AdministradorService {
    void cadastrar(CadastroAdministradorRequest request);
}