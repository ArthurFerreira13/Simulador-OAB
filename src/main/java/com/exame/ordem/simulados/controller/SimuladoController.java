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
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(simuladoService.iniciarSimulado(request));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PutMapping("/{sessaoId}/questoes/{questaoId}/resposta")
    public ResponseEntity<Void> responder(@PathVariable Long sessaoId, @PathVariable int questaoId,
                                          @Valid @RequestBody ResponderQuestaoRequest request) {
        try {
            simuladoService.responderQuestao(sessaoId, questaoId, request);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/{sessaoId}/finalizar")
    public ResponseEntity<ResultadoDTO> finalizar(@PathVariable Long sessaoId) {
        try {
            return ResponseEntity.ok(simuladoService.finalizarSimulado(sessaoId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{sessaoId}/resultado")
    public ResponseEntity<ResultadoDTO> resultado(@PathVariable Long sessaoId) {
        try {
            return ResponseEntity.ok(simuladoService.consultarResultado(sessaoId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/ativa")
    public ResponseEntity<SessaoDTO> sessaoAtiva(@RequestParam String email, @RequestParam String senha) {
        try {
            return ResponseEntity.ok(simuladoService.consultarSessaoAtiva(email, senha));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}