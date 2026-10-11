package com.exame.ordem.questoes.controller;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.RespostasAvulsasService;
import com.exame.ordem.questoes.dto.ProvaResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.questoes.dto.RespostaDetalhadaDTO;
import com.exame.ordem.questoes.dto.RespostaRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exames")
public class QuestaoController {

    private final QuestoesQueryService questoesQueryService;
    private final RespostasAvulsasService respostasAvulsasService;

    QuestaoController(QuestoesQueryService questoesQueryService,
                      RespostasAvulsasService respostasAvulsasService) {
        this.questoesQueryService = questoesQueryService;
        this.respostasAvulsasService = respostasAvulsasService;
    }

    @GetMapping
    public ResponseEntity<List<ProvaResumo>> listarProvas() {
        return ResponseEntity.ok(questoesQueryService.listarProvas());
    }

    @GetMapping("/{numero}/questoes")
    public ResponseEntity<List<QuestaoResumo>> buscarQuestoesDoExame(@PathVariable int numero) {
        return ResponseEntity.ok(questoesQueryService.buscarQuestoesPorProva(numero));
    }

    @GetMapping("/questoes")
    public ResponseEntity<List<QuestaoResumo>> buscarQuestoes(
            @RequestParam(required = false) Integer ano) {
        return ResponseEntity.ok(
                questoesQueryService.buscarPorDisciplinaEAno(null, ano));
    }

    @PostMapping("/questoes/{questaoId}/responder")
    public ResponseEntity<RespostaDetalhadaDTO> responder(
            Authentication auth,
            @PathVariable int questaoId,
            @Valid @RequestBody RespostaRequest request) {
        return ResponseEntity.ok(
                respostasAvulsasService.responder(
                        questaoId, request.alternativa(), usuarioId(auth)));
    }

    private Long usuarioId(Authentication auth) {
        return Long.valueOf(auth.getName());
    }
}