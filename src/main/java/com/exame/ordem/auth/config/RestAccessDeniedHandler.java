package com.exame.ordem.auth.config;

import com.exame.ordem.system.erro.ErroCodigo;
import com.exame.ordem.system.erro.ErroDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;

/**
 * Retorna 403 com o corpo padrão ErroDTO quando o usuário
 * está autenticado mas não tem permissão para o recurso.
 *
 * Hoje praticamente não é acionado (todos os endpoints são USER),
 * mas prepara para quando houver endpoints de ADMIN.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    RestAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        ErroDTO erro = new ErroDTO(
                ErroCodigo.ACESSO_NEGADO,
                "Acesso negado. Você não tem permissão para este recurso.",
                request.getRequestURI(),
                Instant.now(),
                java.util.List.of()
        );

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getWriter(), erro);
    }
}