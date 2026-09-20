package com.exame.ordem.simulados.controller;

import com.exame.ordem.simulados.SimuladoService;
import com.exame.ordem.simulados.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulados")
public class SimuladoController {

    private final SimuladoService simuladoService;

    SimuladoController(SimuladoService simuladoService) {
        this.simuladoService = simuladoService;
    }

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