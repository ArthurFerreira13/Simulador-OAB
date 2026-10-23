package com.exame.ordem.tutor.controller;

import com.exame.ordem.tutor.TutorIaService;
import com.exame.ordem.tutor.dto.ConversaRequest;
import com.exame.ordem.tutor.dto.UsoTutorResumo;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/tutor")
public class TutorIaController {

    private final TutorIaService tutorIaService;

    TutorIaController(TutorIaService tutorIaService) {
        this.tutorIaService = tutorIaService;
    }

    @PostMapping(value = "/conversar", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter conversar(Authentication auth,
                                @Valid @RequestBody ConversaRequest request) {
        return tutorIaService.conversar(
                usuarioId(auth), request.questaoId(), request.mensagem());
    }

    @GetMapping("/uso")
    public ResponseEntity<UsoTutorResumo> consultarUso(Authentication auth) {
        return ResponseEntity.ok(tutorIaService.consultarUso(usuarioId(auth)));
    }

    private Long usuarioId(Authentication auth) {
        return Long.valueOf(auth.getName());
    }
}