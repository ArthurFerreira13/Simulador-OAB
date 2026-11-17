package com.exame.ordem.tutor.service;

import com.exame.ordem.usuarios.PoliticaAcessoService;
import org.springframework.stereotype.Component;

/**
 * Resolve o acesso e os limites diários do tutor para um usuário.
 * Somente assinantes PAGO acessam o tutor; os limites numéricos seguem pendentes em D08.
 */
@Component
public class LimitesPolicy {

    @Deprecated(forRemoval = false, since = "D08-pendente")
    public static final int LIMITE_PAGO_MSG = 200;

    @Deprecated(forRemoval = false, since = "D08-pendente")
    public static final long LIMITE_PAGO_TOKENS = 300_000L;

    private final PoliticaAcessoService politicaAcesso;

    public LimitesPolicy(PoliticaAcessoService politicaAcesso) {
        this.politicaAcesso = politicaAcesso;
    }

    public Limites para(Long usuarioId) {
        return switch (politicaAcesso.estadoDe(usuarioId)) {
            case PAGO -> new Limites(LIMITE_PAGO_MSG, LIMITE_PAGO_TOKENS);
            case TRIAL, GRATUITO -> new Limites(0, 0);
        };
    }

    public record Limites(int mensagens, long tokens) {}
}