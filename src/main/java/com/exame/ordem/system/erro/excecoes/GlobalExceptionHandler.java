package com.exame.ordem.system.erro.excecoes;

import com.exame.ordem.system.erro.ErroCodigo;
import com.exame.ordem.system.erro.ErroDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroDTO> tratarJsonInvalido(HttpMessageNotReadableException e, HttpServletRequest req) {
        return resposta(
                ErroCodigo.REQUISICAO_INVALIDA,
                "Corpo da requisição inválido ou malformado.",
                req,
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroDTO> tratarRotaNaoEncontrada(NoResourceFoundException e, HttpServletRequest req) {
        return resposta(ErroCodigo.RECURSO_NAO_ENCONTRADO,
                "Rota não encontrada: " + req.getRequestURI(),
                req, HttpStatus.NOT_FOUND);
    }

    // ================================================================
    //  [NOVO] Tratamento de cliente desconectado (SSE)
    // ================================================================

    /**
     * Quando o k6 ou o navegador cancela a requisição durante o streaming SSE,
     * o Tomcat avisa o Spring que o socket caiu. Tentar escrever JSON nesse
     * momento gera o erro HttpMessageNotWritableException.
     *
     * A solução é ignorar esses erros silenciosamente (retornar 204 No Content).
     */
    @ExceptionHandler({AsyncRequestNotUsableException.class, HttpMessageNotWritableException.class})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleClientDisconnect(Exception ex) {
        log.debug("Cliente desconectou durante o streaming SSE (erro esperado, ignorado).");
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

    /**
     * Timeout de uma requisição assíncrona (ex.: SSE do tutor). A resposta já foi iniciada como
     * {@code text/event-stream}, então não dá para escrever um {@link ErroDTO} (não há conversor
     * JSON para esse Content-Type). O emitter já é encerrado no {@code onTimeout} do serviço.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void tratarTimeoutAssincrono(AsyncRequestTimeoutException ex, HttpServletRequest req) {
        log.warn("Timeout assíncrono em {} (stream encerrado pelo servidor).", req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroDTO> tratarGenerico(Exception e, HttpServletRequest req, HttpServletResponse res) {
        log.error("Erro interno não tratado em {}", req.getRequestURI(), e);

        // Em respostas SSE o corpo JSON não pode ser escrito: devolver null marca a
        // requisição como tratada e evita o HttpMessageNotWritableException em cascata.
        String contentType = res.getContentType();
        if (contentType != null && contentType.startsWith("text/event-stream")) {
            return null;
        }
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