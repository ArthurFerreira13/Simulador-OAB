package com.exame.ordem.auth.config;

import com.exame.ordem.system.erro.ErroCodigo;
import com.exame.ordem.system.erro.ErroDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;

/**
 * Retorna 401 com o corpo padrão ErroDTO quando o usuário
 * não está autenticado (sem token, token expirado, token inválido).
 *
 * Sem isso, o Spring Security devolve um 401 vazio — inconsistente
 * com o resto da API.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErroDTO erro = new ErroDTO(
                ErroCodigo.NAO_AUTENTICADO,
                "Autenticação necessária. Forneça um token Bearer válido.",
                request.getRequestURI(),
                Instant.now(),
                java.util.List.of()
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getWriter(), erro);
    }
}