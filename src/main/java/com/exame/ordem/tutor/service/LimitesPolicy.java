package com.exame.ordem.tutor.service;

import com.exame.ordem.usuarios.PoliticaAcessoService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Resolve o acesso e os limites diários do tutor para um usuário.
 * Somente assinantes PAGO acessam o tutor; os limites numéricos seguem pendentes em D08.
 *
 * <p>O estado do usuário é consultado em outro módulo (banco) e muda raramente: fica 30 s em
 * cache para não custar uma consulta por mensagem. Mudança de plano vale em até 30 s.
 */
@Component
public class LimitesPolicy {

    @Deprecated(forRemoval = false, since = "D08-pendente")
    public static final int LIMITE_PAGO_MSG = 200;
    @Deprecated(forRemoval = false, since = "D08-pendente")
    public static final long LIMITE_PAGO_TOKENS = 300_000L;

    private final PoliticaAcessoService politicaAcesso;
    private final Cache<Long, Limites> cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    public LimitesPolicy(PoliticaAcessoService politicaAcesso) {
        this.politicaAcesso = politicaAcesso;
    }

    public Limites para(Long usuarioId) {
        return cache.get(usuarioId, this::calcular);
    }

    private Limites calcular(Long usuarioId) {
        return switch (politicaAcesso.estadoDe(usuarioId)) {
            case PAGO -> new Limites(LIMITE_PAGO_MSG, LIMITE_PAGO_TOKENS);
            case TRIAL, GRATUITO -> new Limites(0, 0);
        };
    }

    public record Limites(int mensagens, long tokens) {}
}
