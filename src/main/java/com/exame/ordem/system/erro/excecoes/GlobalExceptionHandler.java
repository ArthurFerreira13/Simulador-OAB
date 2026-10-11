package com.exame.ordem.system.erro.excecoes;


import com.exame.ordem.system.erro.ErroCodigo;
import com.exame.ordem.system.erro.ErroDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ================================================================
    //  Exceções de domínio
    // ================================================================

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroDTO> tratarNaoEncontrado(RecursoNaoEncontradoException e, HttpServletRequest req) {
        return resposta(ErroCodigo.RECURSO_NAO_ENCONTRADO, e.getMessage(), req, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflitoDeEstadoException.class)
    public ResponseEntity<ErroDTO> tratarConflito(ConflitoDeEstadoException e, HttpServletRequest req) {
        return resposta(ErroCodigo.CONFLITO_DE_ESTADO, e.getMessage(), req, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroDTO> tratarCredenciais(CredenciaisInvalidasException e, HttpServletRequest req) {
        return resposta(ErroCodigo.CREDENCIAIS_INVALIDAS, e.getMessage(), req, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroDTO> tratarRegra(RegraDeNegocioException e, HttpServletRequest req) {
        return resposta(ErroCodigo.REGRA_DE_NEGOCIO, e.getMessage(), req, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    public ResponseEntity<ErroDTO> tratarLimite(LimiteExcedidoException e, HttpServletRequest req) {
        return resposta(ErroCodigo.LIMITE_EXCEDIDO, e.getMessage(), req, HttpStatus.TOO_MANY_REQUESTS);
    }

    // ================================================================
    //  Exceções do Spring MVC
    // ================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroDTO> tratarValidacao(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<String> detalhes = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();

        ErroDTO dto = new ErroDTO(
                ErroCodigo.REQUISICAO_INVALIDA,
                "Um ou mais campos estão inválidos.",
                req.getRequestURI(),
                Instant.now(),
                detalhes);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
    }

    /**
     * JSON malformado no body (ex.: aspas faltando) — é erro do cliente.
     * Devolve 400 em vez de 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroDTO> tratarJsonInvalido(HttpMessageNotReadableException e, HttpServletRequest req) {
        return resposta(
                ErroCodigo.REQUISICAO_INVALIDA,
                "Corpo da requisição inválido ou malformado.",
                req,
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Trata rotas inexistentes (o Spring tenta resolver como recurso estático
     * e lança NoResourceFoundException). Sem isso, viraria 500 no handler genérico.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroDTO> tratarRotaNaoEncontrada(NoResourceFoundException e, HttpServletRequest req) {
        return resposta(ErroCodigo.RECURSO_NAO_ENCONTRADO,
                "Rota não encontrada: " + req.getRequestURI(),
                req, HttpStatus.NOT_FOUND);
    }

    // ================================================================
    //  Fallbacks (exceções genéricas ainda não migradas)
    // ================================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroDTO> tratarIllegalArgument(IllegalArgumentException e, HttpServletRequest req) {
        return resposta(ErroCodigo.REQUISICAO_INVALIDA, e.getMessage(), req, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErroDTO> tratarIllegalState(IllegalStateException e, HttpServletRequest req) {
        return resposta(ErroCodigo.CONFLITO_DE_ESTADO, e.getMessage(), req, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroDTO> tratarGenerico(Exception e, HttpServletRequest req) {
        log.error("Erro interno não tratado em {}", req.getRequestURI(), e);
        return resposta(ErroCodigo.ERRO_INTERNO, "Erro interno inesperado.", req, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ================================================================
    //  Helper
    // ================================================================

    private ResponseEntity<ErroDTO> resposta(ErroCodigo codigo, String mensagem,
                                             HttpServletRequest req, HttpStatus status) {
        return ResponseEntity.status(status)
                .body(new ErroDTO(codigo, mensagem, req.getRequestURI()));
    }
}