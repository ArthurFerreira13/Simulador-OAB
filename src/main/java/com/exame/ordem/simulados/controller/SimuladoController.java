package com.exame.ordem.simulados.controller;

import com.exame.ordem.simulados.SimuladoConsultaService;
import com.exame.ordem.simulados.SimuladoRevisaoService;
import com.exame.ordem.simulados.SimuladoService;
import com.exame.ordem.simulados.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulados")
public class SimuladoController {

    private final SimuladoService simuladoService;
    private final SimuladoConsultaService consultaService;
    private final SimuladoRevisaoService revisaoService;

    SimuladoController(SimuladoService simuladoService,
                       SimuladoConsultaService consultaService,
                       SimuladoRevisaoService revisaoService) {
        this.simuladoService = simuladoService;
        this.consultaService = consultaService;
        this.revisaoService = revisaoService;
    }

    // ================================================================
    //  Ciclo de vida
    // ================================================================

    @PostMapping("/iniciar")
    public ResponseEntity<SessaoDTO> iniciar(Authentication auth,
                                             @Valid @RequestBody IniciarSimuladoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(simuladoService.iniciarSimulado(usuarioId(auth), request));
    }

    @PutMapping("/{sessaoId}/questoes/{questaoId}/resposta")
    public ResponseEntity<Void> responder(Authentication auth,
                                          @PathVariable Long sessaoId,
                                          @PathVariable int questaoId,
                                          @Valid @RequestBody ResponderQuestaoRequest request) {
        simuladoService.responderQuestao(usuarioId(auth), sessaoId, questaoId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sessaoId}/finalizar")
    public ResponseEntity<ResultadoDTO> finalizar(Authentication auth,
                                                  @PathVariable Long sessaoId) {
        return ResponseEntity.ok(simuladoService.finalizarSimulado(usuarioId(auth), sessaoId));
    }

    @PostMapping("/{sessaoId}/abandonar")
    public ResponseEntity<Void> abandonar(Authentication auth,
                                          @PathVariable Long sessaoId) {
        simuladoService.abandonarSimulado(usuarioId(auth), sessaoId);
        return ResponseEntity.noContent().build();
    }

    // ================================================================
    //  Leitura
    // ================================================================

    @GetMapping("/ativa")
    public ResponseEntity<SessaoDTO> sessaoAtiva(Authentication auth) {
        return ResponseEntity.ok(consultaService.consultarSessaoAtiva(usuarioId(auth)));
    }

    @GetMapping("/{sessaoId}/resultado")
    public ResponseEntity<ResultadoDTO> resultado(Authentication auth,
                                                  @PathVariable Long sessaoId) {
        return ResponseEntity.ok(consultaService.consultarResultado(usuarioId(auth), sessaoId));
    }

    // ================================================================
    //  RF03.3 — Marcar / desmarcar questão para revisão
    // ================================================================

    @PostMapping("/{sessaoId}/questoes/{questaoId}/revisao")
    public ResponseEntity<Void> marcarRevisao(Authentication auth,
                                              @PathVariable Long sessaoId,
                                              @PathVariable int questaoId) {
        revisaoService.marcarRevisao(usuarioId(auth), sessaoId, questaoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{sessaoId}/questoes/{questaoId}/revisao")
    public ResponseEntity<Void> desmarcarRevisao(Authentication auth,
                                                 @PathVariable Long sessaoId,
                                                 @PathVariable int questaoId) {
        revisaoService.desmarcarRevisao(usuarioId(auth), sessaoId, questaoId);
        return ResponseEntity.noContent().build();
    }

    // ================================================================
    //  Helper
    // ================================================================

    private Long usuarioId(Authentication auth) {
        return Long.valueOf(auth.getName());
    }
}