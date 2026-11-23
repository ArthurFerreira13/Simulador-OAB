package com.exame.ordem.legislacao.controller;

import com.exame.ordem.legislacao.service.ImportacaoLegislacaoService;
import com.exame.ordem.legislacao.service.ImportacaoLegislacaoService.ComandoImportacao;
import com.exame.ordem.legislacao.service.ImportacaoLegislacaoService.ResultadoImportacao;
import com.exame.ordem.legislacao.service.ImportacaoLegislacaoService.ResultadoPublicacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Administração da legislação. Desligado por padrão: exige
 * {@code app.legislacao.admin.habilitado=true} E papel ADMIN.
 *
 * <p>ATENÇÃO: {@code @PreAuthorize} só vale com {@code @EnableMethodSecurity} ativo no projeto.
 * Confirme antes de habilitar a propriedade em qualquer ambiente exposto.
 */
@RestController
@RequestMapping("/api/admin/legislacao")
@ConditionalOnProperty(name = "app.legislacao.admin.habilitado", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class LegislacaoAdminController {

    private final ImportacaoLegislacaoService importacao;

    LegislacaoAdminController(ImportacaoLegislacaoService importacao) {
        this.importacao = importacao;
    }

    public record ImportarNormaRequest(
            @NotBlank @Size(max = 160) String identificadorExterno,
            @NotBlank @Size(max = 80) String tipo,
            @Size(max = 80) String numero,
            Integer ano,
            @NotBlank @Size(max = 500) String nome,
            String ementa,
            Integer disciplinaId,
            @NotBlank @Size(max = 2000) String fonteUrl,
            @NotBlank @Size(max = 2_000_000) String texto,
            boolean publicar
    ) {}

    @PostMapping("/normas/importar")
    public ResponseEntity<ResultadoImportacao> importar(@Valid @RequestBody ImportarNormaRequest r) {
        var resultado = importacao.importar(new ComandoImportacao(
                r.identificadorExterno(), r.tipo(), r.numero(), r.ano(), r.nome(), r.ementa(),
                r.disciplinaId(), r.fonteUrl(), r.texto(), r.publicar()));
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/versoes/{versaoId}/publicar")
    public ResponseEntity<ResultadoPublicacao> publicar(
            @PathVariable UUID versaoId,
            @RequestParam(defaultValue = "false") boolean forcar) {
        return ResponseEntity.ok(importacao.publicar(versaoId, forcar));
    }
}
