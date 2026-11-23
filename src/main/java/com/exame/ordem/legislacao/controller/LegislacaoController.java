package com.exame.ordem.legislacao.controller;

import com.exame.ordem.legislacao.LegislacaoQuery;
import com.exame.ordem.legislacao.dto.ArtigoDetalhe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Consulta de artigo para o link de citação das fontes do tutor. */
@RestController
@RequestMapping("/api/legislacao")
public class LegislacaoController {

    private final LegislacaoQuery legislacao;

    LegislacaoController(LegislacaoQuery legislacao) {
        this.legislacao = legislacao;
    }

    @GetMapping("/artigos/{artigoId}")
    public ResponseEntity<ArtigoDetalhe> artigo(@PathVariable UUID artigoId) {
        return legislacao.artigo(artigoId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
