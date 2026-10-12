package com.exame.ordem.administradores.controller;

import com.exame.ordem.administradores.AdministradorService;
import com.exame.ordem.administradores.dto.CadastroAdministradorRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Cadastro bootstrap de administrador.
 * Disponível SOMENTE no perfil "dev" — em produção o bean não é registrado
 * e a rota responde 404, sem vazar a existência do endpoint.
 */
@RestController
@RequestMapping("/api/admin/administradores")
@Profile("dev")
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