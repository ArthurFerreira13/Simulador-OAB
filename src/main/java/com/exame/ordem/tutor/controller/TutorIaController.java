package com.exame.ordem.tutor.controller;

import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import com.exame.ordem.tutor.TutorIaService;
import com.exame.ordem.tutor.dto.ConversaRequest;
import com.exame.ordem.tutor.dto.UsoTutorResumo;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/tutor")
public class TutorIaController {

    private static final Logger log = LoggerFactory.getLogger(TutorIaController.class);

    private final TutorIaService tutorIaService;

    TutorIaController(TutorIaService tutorIaService) {
        this.tutorIaService = tutorIaService;
    }

    @PostMapping(value = "/conversar", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter conversar(Authentication auth, @Valid @RequestBody ConversaRequest request) {
        try {
            return tutorIaService.conversar(usuarioId(auth), request.questaoId(), request.mensagem());
        } catch (RegraDeNegocioException e) {
            throw e; // Repassa para o Handler abaixo gerar o 422
        } catch (Exception e) {
            log.warn("Falha síncrona em /conversar (usuario {}): {}", auth.getName(), e.getMessage());
            return sseErro("Tutor indisponível no momento. Tente novamente em alguns segundos.");
        }
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroDTO> handleCota(RegraDeNegocioException e) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY) // 422
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErroDTO.limiteDeUso(e.getMessage()));
    }

    @GetMapping("/uso")
    public ResponseEntity<UsoTutorResumo> consultarUso(Authentication auth) {
        return ResponseEntity.ok(tutorIaService.consultarUso(usuarioId(auth)));
    }

    private Long usuarioId(Authentication auth) {
        return Long.valueOf(auth.getName());
    }

    private SseEmitter sseErro(String mensagem) {
        SseEmitter emitter = new SseEmitter(10_000L);
        try {
            String json = "{\"mensagem\":\"" + mensagem.replace("\"", "\\\"") + "\"}";
            emitter.send(SseEmitter.event().name("error").data(json));
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public record ErroDTO(
            String codigo,
            String mensagem,
            String correlationId,
            List<String> detalhes
    ) {
        public static ErroDTO limiteDeUso(String mensagem) {
            return new ErroDTO("LIMITE_DE_USO", mensagem, null, List.of());
        }
    }
}