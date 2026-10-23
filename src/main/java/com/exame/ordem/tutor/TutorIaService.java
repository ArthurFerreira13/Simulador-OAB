package com.exame.ordem.tutor;

import com.exame.ordem.tutor.dto.UsoTutorResumo;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface TutorIaService {

    SseEmitter conversar(Long usuarioId, Integer questaoId, String mensagem);

    UsoTutorResumo consultarUso(Long usuarioId);
}