package com.exame.ordem.questoes.controller;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/questoes")
public class QuestaoConsultaController {

    private final QuestoesQueryService questoesQueryService;

    QuestaoConsultaController(QuestoesQueryService questoesQueryService) {
        this.questoesQueryService = questoesQueryService;
    }

    /** Retorna uma questão individual (sem expor o gabarito). */
    @GetMapping("/{id}")
    public ResponseEntity<QuestaoResumo> buscarPorId(@PathVariable int id) {
        return ResponseEntity.ok(questoesQueryService.buscarQuestaoPorId(id));
    }
}