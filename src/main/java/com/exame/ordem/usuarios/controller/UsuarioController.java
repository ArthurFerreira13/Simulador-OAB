package com.exame.ordem.usuarios.controller;

import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.AtualizarPerfilRequest;
import com.exame.ordem.usuarios.dto.CadastroUsuarioRequest;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    @GetMapping("/perfil")
    public ResponseEntity<UsuarioResumo> consultarPerfil(Authentication auth) {
        return ResponseEntity.ok(usuarioService.buscarPorId(usuarioId(auth)));
    }

    @PutMapping("/perfil")
    public ResponseEntity<UsuarioResumo> atualizarPerfil(
            Authentication auth,
            @Valid @RequestBody AtualizarPerfilRequest request) {
        return ResponseEntity.ok(usuarioService.atualizarPerfil(usuarioId(auth), request));
    }

    @DeleteMapping
    public ResponseEntity<Void> excluirConta(Authentication auth) {
        usuarioService.excluirConta(usuarioId(auth));
        return ResponseEntity.noContent().build();
    }

    private Long usuarioId(Authentication auth) {
        return Long.valueOf(auth.getName());
    }
}