// com/exame/ordem/usuarios/controller/UsuarioController.java
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
        try {
            UsuarioResumo criado = usuarioService.cadastrar(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(criado);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

    }
    @PutMapping("/perfil")
    public ResponseEntity<Void> atualizarPerfil(@Valid @RequestBody AtualizarPerfilRequest request) {
        try {
            usuarioService.atualizarPerfil(request);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
        @DeleteMapping
        public ResponseEntity<Void> excluirConta(@Valid @RequestBody ExcluirContaRequest request) {
            try {
                usuarioService.excluirConta(request);
                return ResponseEntity.noContent().build();
            } catch (SecurityException e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            } catch (IllegalStateException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }

    @GetMapping("/perfil")
    public ResponseEntity<UsuarioResumo> consultarPerfil(
            @RequestParam String email,
            @RequestParam String senha) {
        try {
            return ResponseEntity.ok(usuarioService.consultarPerfil(email, senha));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}