package com.exame.ordem.tutor.service;

import com.exame.ordem.usuarios.PoliticaAcessoService;
import com.exame.ordem.usuarios.EstadoAcesso;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LimitesPolicyTest {

    @Test
    void apenasUsuarioPagoRecebeLimitesDoTutor() {
        var politica = mock(PoliticaAcessoService.class);
        when(politica.estadoDe(42L)).thenReturn(EstadoAcesso.PAGO);

        var limites = new LimitesPolicy(politica).para(42L);

        assertThat(limites.mensagens()).isEqualTo(LimitesPolicy.LIMITE_PAGO_MSG);
        assertThat(limites.tokens()).isEqualTo(LimitesPolicy.LIMITE_PAGO_TOKENS);
    }

    @Test
    void trialNaoTemAcessoAoTutor() {
        assertSemAcesso(EstadoAcesso.TRIAL);
    }

    @Test
    void gratuitoNaoTemAcessoAoTutor() {
        assertSemAcesso(EstadoAcesso.GRATUITO);
    }

    private static void assertSemAcesso(EstadoAcesso estado) {
        var politica = mock(PoliticaAcessoService.class);
        when(politica.estadoDe(42L)).thenReturn(estado);

        var limites = new LimitesPolicy(politica).para(42L);

        assertThat(limites.mensagens()).isZero();
        assertThat(limites.tokens()).isZero();
    }
}