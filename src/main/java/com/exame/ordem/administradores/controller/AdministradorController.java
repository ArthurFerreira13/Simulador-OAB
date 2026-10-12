package com.exame.ordem.administradores.controller;

import com.exame.ordem.administradores.AdministradorService;
import com.exame.ordem.administradores.dto.CadastroAdministradorRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Cadastro bootstrap de administrador.
 * A proteção vem da chave (app.admin.chave-cadastro) no body.
 */
@RestController
@RequestMapping("/api/admin/administradores")
public class AdministradorController {

    private final AdministradorService administradorService;

    AdministradorController(AdministradorService administradorService) {
        this.administradorService = administradorService;
    }

    @PostMapping
    public ResponseEntity<Void> cadastrar(@Valid @RequestBody CadastroAdministradorRequest request) {
        administradorService.cadastrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}