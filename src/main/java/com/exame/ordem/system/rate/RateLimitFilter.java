package com.exame.ordem.system.rate;

import com.exame.ordem.system.erro.ErroCodigo;
import com.exame.ordem.system.erro.ErroDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Rate limit por IP nos endpoints sensíveis (login, cadastro).
 *
 * <p><b>Importante:</b> NÃO é um {@code @Component}. É instanciado manualmente
 * em {@code SecurityConfig} e registrado no filter chain, para evitar que o
 * Spring o envolva em proxy CGLIB (o que quebraria {@code OncePerRequestFilter},
 * cujo {@code doFilter} é final e cujo {@code logger} final ficaria nulo).
 *
 * <p><b>Escala:</b> buckets em memória (Caffeine), por instância. Suficiente
 * para MVP com 1 instância; migrar para Redis ao escalar horizontalmente (RNF03).
 *
 * <p><b>IP:</b> usa {@code request.getRemoteAddr()}. Em produção atrás de proxy
 * confiável, configurar {@code server.forward-headers-strategy=native} e
 * {@code server.tomcat.remoteip.internal-proxies} para que o Tomcat reescreva
 * o IP de forma segura. NÃO confiar em {@code X-Forwarded-For} lido direto.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties props;
    private final JsonMapper jsonMapper;

    /**
     * Buckets com expiração (evita vazamento de memória) e limite de tamanho
     * (mitiga DoS via IP forjado).
     */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(100_000)
            .build();

    public RateLimitFilter(RateLimitProperties props, JsonMapper jsonMapper) {
        this.props = props;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!props.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String ruleKey = ruleKeyFor(request);
        if (ruleKey == null) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitProperties.Rule rule = props.getRules().get(ruleKey);
        if (rule == null || rule.getCapacity() <= 0) {
            chain.doFilter(request, response);
            return;
        }

        // Chave por IP real + endpoint. Não confia em headers do cliente.
        String bucketKey = request.getRemoteAddr() + ":" + ruleKey;
        Bucket bucket = buckets.get(bucketKey, k -> novoBucket(rule));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1L, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        log.warn("Rate limit excedido: ip={} endpoint={} retryAfter={}s",
                request.getRemoteAddr(), request.getRequestURI(), retryAfterSeconds);

        escreverResposta429(response, request, retryAfterSeconds);
    }

    /**
     * Mapeia método+path para chave lógica em {@link RateLimitProperties#getRules()}.
     * Retorna {@code null} quando o endpoint não tem rate limit.
     *
     * <p>Escopo inicial: login e cadastro. Quando entrarem refresh token e
     * recuperação de senha, incluir aqui.
     */
    private String ruleKeyFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if ("/api/auth/login".equals(path)) {
            return "login";
        }
        if ("/api/usuarios".equals(path)) {
            return "cadastro";
        }
        return null;
    }

    private Bucket novoBucket(RateLimitProperties.Rule rule) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rule.getCapacity())
                        .refillIntervally(rule.getCapacity(), rule.getWindow())
                        .build())
                .build();
    }

    private void escreverResposta429(HttpServletResponse response,
                                     HttpServletRequest request,
                                     long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // Reaproveita o formato padrão de erro da API (mesmo do GlobalExceptionHandler).
        ErroDTO erro = new ErroDTO(
                ErroCodigo.RATE_LIMIT_EXCEDIDO,
                "Muitas tentativas. Tente novamente em " + retryAfterSeconds + " segundos.",
                request.getRequestURI(),
                Instant.now(),
                List.of()
        );

        jsonMapper.writeValue(response.getWriter(), erro);
    }
}