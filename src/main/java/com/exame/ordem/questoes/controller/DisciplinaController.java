package com.exame.ordem.questoes.controller;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.DisciplinaResumo;
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

    @GetMapping
    public ResponseEntity<List<DisciplinaResumo>> listar() {
        return ResponseEntity.ok(questoesQueryService.listarDisciplinas());
    }
}