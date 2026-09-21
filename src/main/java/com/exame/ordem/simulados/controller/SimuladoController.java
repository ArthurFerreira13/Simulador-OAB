package com.exame.ordem.simulados.controller;

import com.exame.ordem.simulados.SimuladoService;
import com.exame.ordem.simulados.dto.*;
import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulados")
public class SimuladoController {

    private final SimuladoService simuladoService;
    private final UsuarioService usuarioService;

    SimuladoController(SimuladoService simuladoService, UsuarioService usuarioService) {
        this.simuladoService = simuladoService;
        this.usuarioService = usuarioService;
    }

    // ================================================================
    //  Endpoints existentes
    // ================================================================

    @PostMapping("/iniciar")
    public ResponseEntity<SessaoDTO> iniciar(@Valid @RequestBody IniciarSimuladoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(simuladoService.iniciarSimulado(request));
    }

    @PutMapping("/{sessaoId}/questoes/{questaoId}/resposta")
    public ResponseEntity<Void> responder(@PathVariable Long sessaoId, @PathVariable int questaoId,
                                          @Valid @RequestBody ResponderQuestaoRequest request) {
        simuladoService.responderQuestao(sessaoId, questaoId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sessaoId}/finalizar")
    public ResponseEntity<ResultadoDTO> finalizar(@PathVariable Long sessaoId) {
        return ResponseEntity.ok(simuladoService.finalizarSimulado(sessaoId));
    }

    @GetMapping("/{sessaoId}/resultado")
    public ResponseEntity<ResultadoDTO> resultado(@PathVariable Long sessaoId) {
        return ResponseEntity.ok(simuladoService.consultarResultado(sessaoId));
    }

    @GetMapping("/ativa")
    public ResponseEntity<SessaoDTO> sessaoAtiva(@RequestParam String email, @RequestParam String senha) {
        return ResponseEntity.ok(simuladoService.consultarSessaoAtiva(email, senha));
    }

    // ================================================================
    //  RF03.3 — Marcar / desmarcar questão para revisão
    // ================================================================

    @PostMapping("/{sessaoId}/questoes/{questaoId}/revisao")
    public ResponseEntity<Void> marcarRevisao(
            @PathVariable Long sessaoId,
            @PathVariable int questaoId,
            @Valid @RequestBody CredenciaisRequest credenciais) {
        UsuarioResumo usuario = usuarioService.consultarPerfil(credenciais.email(), credenciais.senha());
        simuladoService.marcarRevisao(sessaoId, questaoId, usuario.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{sessaoId}/questoes/{questaoId}/revisao")
    public ResponseEntity<Void> desmarcarRevisao(
            @PathVariable Long sessaoId,
            @PathVariable int questaoId,
            @RequestParam String email,
            @RequestParam String senha) {
        UsuarioResumo usuario = usuarioService.consultarPerfil(email, senha);
        simuladoService.desmarcarRevisao(sessaoId, questaoId, usuario.id());
        return ResponseEntity.noContent().build();
    }

    // ================================================================
    //  RF03.6 — Abandono manual
    // ================================================================

    @PostMapping("/{sessaoId}/abandonar")
    public ResponseEntity<Void> abandonar(
            @PathVariable Long sessaoId,
            @Valid @RequestBody CredenciaisRequest credenciais) {
        UsuarioResumo usuario = usuarioService.consultarPerfil(credenciais.email(), credenciais.senha());
        simuladoService.abandonarSimulado(sessaoId, usuario.id());
        return ResponseEntity.noContent().build();
    }

    // ================================================================
    //  Exception handlers
    // ================================================================

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErroDTO> tratarSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErroDTO("NAO_AUTORIZADO", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroDTO> tratarIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErroDTO("RECURSO_NAO_ENCONTRADO", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErroDTO> tratarIllegalStateException(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErroDTO("CONFLITO_DE_ESTADO", e.getMessage()));
    }
}