// com/exame/ordem/questoes/controller/QuestaoController.java
package com.exame.ordem.questoes.controller;

import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.RespostaDetalhadaDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<RespostaDetalhadaDTO> responder(
            @PathVariable int questaoId,
            @RequestBody RespostaRequest request) {
        try {
            return ResponseEntity.ok(questoesQueryService.verificarResposta(questaoId, request.alternativaId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/questoes/disciplina/{disciplinaId}")
    public ResponseEntity<List<QuestaoResumo>> buscarQuestoesPorDisciplina(@PathVariable Long disciplinaId) {
        return ResponseEntity.ok(questoesQueryService.buscarQuestoesPorDisciplina(disciplinaId));
    }

    record RespostaRequest(Long alternativaId) {}
}