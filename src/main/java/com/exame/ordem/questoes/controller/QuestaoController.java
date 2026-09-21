// com/exame/ordem/questoes/controller/QuestaoController.java
package com.exame.ordem.questoes.controller;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.ProvaResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;
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

    /** Lista todas as provas disponíveis (id, título, ano). */
    @GetMapping
    public ResponseEntity<List<ProvaResumo>> listarProvas() {
        return ResponseEntity.ok(questoesQueryService.listarProvas());
    }

    @GetMapping("/{numero}/questoes")
    public ResponseEntity<List<QuestaoResumo>> buscarQuestoesDoExame(@PathVariable int numero) {
        try {
            return ResponseEntity.ok(questoesQueryService.buscarQuestoesPorProva(numero));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Filtro combinável de questões por disciplina e/ou ano. Ambos opcionais:
     *
     *   GET /api/exames/questoes                              → todas
     *   GET /api/exames/questoes?ano=2025                     → só ano
     *   GET /api/exames/questoes?disciplinaId=1               → só disciplina
     *   GET /api/exames/questoes?disciplinaId=1&ano=2025      → os dois
     */
    @GetMapping("/questoes")
    public ResponseEntity<List<QuestaoResumo>> buscarQuestoes(
            @RequestParam(required = false) Long disciplinaId,
            @RequestParam(required = false) Integer ano) {
        return ResponseEntity.ok(
                questoesQueryService.buscarPorDisciplinaEAno(disciplinaId, ano));
    }

    @PostMapping("/questoes/{questaoId}/responder")
    public ResponseEntity<RespostaDetalhadaDTO> responder(
            @PathVariable int questaoId,
            @RequestBody RespostaRequest request) {
        try {
            return ResponseEntity.ok(
                    questoesQueryService.verificarResposta(questaoId, request.alternativaId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    record RespostaRequest(Long alternativaId) {}
}