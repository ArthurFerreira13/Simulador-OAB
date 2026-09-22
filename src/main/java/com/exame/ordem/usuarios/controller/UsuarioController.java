package com.exame.ordem.usuarios.controller;

import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.AtualizarPerfilRequest;
import com.exame.ordem.usuarios.dto.CadastroUsuarioRequest;
import com.exame.ordem.usuarios.dto.ExcluirContaRequest;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResumo> cadastrar(@Valid @RequestBody CadastroUsuarioRequest request) {
        UsuarioResumo criado = usuarioService.cadastrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/perfil")
    public ResponseEntity<UsuarioResumo> atualizarPerfil(@Valid @RequestBody AtualizarPerfilRequest request) {
        UsuarioResumo atualizado = usuarioService.atualizarPerfil(request);
        return ResponseEntity.ok(atualizado);
    }

    @DeleteMapping
    public ResponseEntity<Void> excluirConta(@Valid @RequestBody ExcluirContaRequest request) {
        usuarioService.excluirConta(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/perfil")
    public ResponseEntity<UsuarioResumo> consultarPerfil(
            @RequestParam String email,
            @RequestParam String senha) {
        return ResponseEntity.ok(usuarioService.consultarPerfil(email, senha));
    }
}