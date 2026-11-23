package com.exame.ordem.tutor.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class CotaSessaoTest {

    private static final Long USUARIO_ID = 42L;
    private static final LocalDate DATA = LocalDate.of(2026, 11, 17);
    private static final int TOKENS_ESTIMADOS = 4096;

    @Test
    void contabilizaConsumoRealApenasUmaVez() {
        TutorCotaSeguro cotaSeguro = mock(TutorCotaSeguro.class);
        CotaSessao sessao = new CotaSessao(USUARIO_ID, DATA, cotaSeguro, TOKENS_ESTIMADOS);

        sessao.confirmarConsumo(712);
        sessao.confirmarConsumo(800);

        verify(cotaSeguro).somarTokens(USUARIO_ID, DATA, 712);
        verifyNoMoreInteractions(cotaSeguro);
    }

    @Test
    void estornaMensagemApenasUmaVez() {
        TutorCotaSeguro cotaSeguro = mock(TutorCotaSeguro.class);
        CotaSessao sessao = new CotaSessao(USUARIO_ID, DATA, cotaSeguro, TOKENS_ESTIMADOS);

        sessao.estornarUmaVez();
        sessao.estornarUmaVez();

        verify(cotaSeguro).estornar(USUARIO_ID, DATA);
        verifyNoMoreInteractions(cotaSeguro);
    }

    @Test
    void falhaDepoisDeEntregarTokenContabilizaEstimativaConservadora() {
        TutorCotaSeguro cotaSeguro = mock(TutorCotaSeguro.class);
        CotaSessao sessao = new CotaSessao(USUARIO_ID, DATA, cotaSeguro, TOKENS_ESTIMADOS);

        sessao.registrarPrimeiroToken();
        sessao.encerrarComEstornoSeNecessario();

        verify(cotaSeguro).somarTokens(USUARIO_ID, DATA, TOKENS_ESTIMADOS);
        verifyNoMoreInteractions(cotaSeguro);
    }
}
