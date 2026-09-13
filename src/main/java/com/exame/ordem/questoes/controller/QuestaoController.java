// com/exame/ordem/questoes/controller/QuestaoController.java
package com.exame.ordem.questoes.controller;

import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.questoes.QuestoesQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exames")
public class QuestaoController {

    private final QuestoesQueryService questoesQueryService;

    QuestaoController(QuestoesQueryService questoesQueryService) {
        this.questoesQueryService = questoesQueryService;
    }

    @GetMapping("/{numero}/questoes")
    public ResponseEntity<List<QuestaoResumo>> buscarQuestoesDoExame(@PathVariable int numero) {
        try {
            return ResponseEntity.ok(questoesQueryService.buscarQuestoesPorProva(numero));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    @PostMapping("/questoes/{questaoId}/responder")
    public ResponseEntity<Map<String, Boolean>> responder(
            @PathVariable int questaoId,
            @RequestBody RespostaRequest request) {
        try {
            boolean correta = questoesQueryService.verificarResposta(questaoId, request.alternativaId());
            return ResponseEntity.ok(Map.of("correta", correta));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    record RespostaRequest(Long alternativaId) {}
}