package com.exame.ordem.tutor.controller;

import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import com.exame.ordem.tutor.TutorAcessoNegadoException;
import com.exame.ordem.tutor.TutorIaService;
import com.exame.ordem.tutor.TutorIndisponivelException;
import com.exame.ordem.tutor.TutorLimiteAtingidoException;
import com.exame.ordem.tutor.TutorQuestaoNaoEncontradaException;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * API do tutor. Contrato de erros (JSON, antes de abrir o stream):
 * <ul>
 *   <li>401 usuário não identificado</li>
 *   <li>403 {@code ACESSO_NEGADO}: o plano não inclui o tutor (paywall)</li>
 *   <li>404 {@code QUESTAO_NAO_ENCONTRADA}</li>
 *   <li>422 {@code REGRA_DE_NEGOCIO}: outras regras de negócio</li>
 *   <li>429 {@code LIMITE_DE_USO}: cota diária ou streams simultâneos do usuário</li>
 *   <li>503 {@code TUTOR_INDISPONIVEL} (+ Retry-After): capacidade global esgotada</li>
 * </ul>
 */
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
        Long usuarioId = usuarioId(auth);
        try {
            return tutorIaService.conversar(usuarioId, request.questaoId(), request.mensagem());
        } catch (TutorAcessoNegadoException | TutorLimiteAtingidoException
                 | TutorIndisponivelException | TutorQuestaoNaoEncontradaException
                 | RegraDeNegocioException e) {
            throw e; // tratadas pelos handlers abaixo, com status e código próprios
        } catch (Exception e) {
            log.warn("Falha síncrona em /conversar (usuario {}): {}", usuarioId, e.getMessage());
            return sseErro("Tutor indisponível no momento. Tente novamente em alguns segundos.");
        }
    }

    @GetMapping("/uso")
    public ResponseEntity<UsoTutorResumo> consultarUso(Authentication auth) {
        return ResponseEntity.ok(tutorIaService.consultarUso(usuarioId(auth)));
    }

    @ExceptionHandler(TutorAcessoNegadoException.class)
    public ResponseEntity<ErroDTO> handleAcessoNegado(TutorAcessoNegadoException e) {
        return json(HttpStatus.FORBIDDEN, ErroDTO.de("ACESSO_NEGADO", e.getMessage()));
    }

    @ExceptionHandler(TutorLimiteAtingidoException.class)
    public ResponseEntity<ErroDTO> handleLimite(TutorLimiteAtingidoException e) {
        return json(HttpStatus.TOO_MANY_REQUESTS, ErroDTO.limiteDeUso(e.getMessage()));
    }

    @ExceptionHandler(TutorIndisponivelException.class)
    public ResponseEntity<ErroDTO> handleIndisponivel(TutorIndisponivelException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErroDTO.de("TUTOR_INDISPONIVEL", e.getMessage()));
    }

    @ExceptionHandler(TutorQuestaoNaoEncontradaException.class)
    public ResponseEntity<ErroDTO> handleQuestao(TutorQuestaoNaoEncontradaException e) {
        return json(HttpStatus.NOT_FOUND, ErroDTO.de("QUESTAO_NAO_ENCONTRADA", e.getMessage()));
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroDTO> handleRegra(RegraDeNegocioException e) {
        return json(HttpStatus.UNPROCESSABLE_ENTITY, ErroDTO.de("REGRA_DE_NEGOCIO", e.getMessage()));
    }

    private static ResponseEntity<ErroDTO> json(HttpStatus status, ErroDTO corpo) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(corpo);
    }

    private Long usuarioId(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
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
        public static ErroDTO de(String codigo, String mensagem) {
            return new ErroDTO(codigo, mensagem, null, List.of());
        }

        public static ErroDTO limiteDeUso(String mensagem) {
            return de("LIMITE_DE_USO", mensagem);
        }
    }
}
