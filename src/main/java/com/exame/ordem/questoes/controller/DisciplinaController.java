package com.exame.ordem.questoes.controller;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.DisciplinaResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/disciplinas")
public class DisciplinaController {

    private final QuestoesQueryService questoesQueryService;

    DisciplinaController(QuestoesQueryService questoesQueryService) {
        this.questoesQueryService = questoesQueryService;
    }

    /** Lista todas as 20 disciplinas do edital. */
    @GetMapping
    public ResponseEntity<List<DisciplinaResumo>> listar() {
        return ResponseEntity.ok(questoesQueryService.listarDisciplinas());
    }

    /**
     * Lista as questões de uma disciplina, com filtro opcional por ano.
     *
     *   GET /api/disciplinas/3/questoes              → todas de Constitucional
     *   GET /api/disciplinas/3/questoes?ano=2024     → só as de 2024
     */
    @GetMapping("/{id}/questoes")
    public ResponseEntity<List<QuestaoResumo>> questoesDaDisciplina(
            @PathVariable Long id,
            @RequestParam(required = false) Integer ano) {
        return ResponseEntity.ok(
                questoesQueryService.buscarPorDisciplinaEAno(id, ano));
    }
}