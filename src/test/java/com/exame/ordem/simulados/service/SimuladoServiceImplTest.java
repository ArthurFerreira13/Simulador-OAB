package com.exame.ordem.simulados.service;

import com.exame.ordem.simulados.internal.*;
import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimuladoRevisaoServiceImplTest {

    @Mock SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;
    @Mock SimuladoSessaoValidator validator;

    @InjectMocks SimuladoRevisaoServiceImpl service;

    @Test
    @DisplayName("marcarRevisao → marca vínculo de sessão ativa")
    void marcarRevisao_sucesso() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        SimuladoSessaoQuestao vinculo = vinculo(23L, 1, false);

        when(validator.buscarSessaoDoUsuario(23L, 1L)).thenReturn(sessao);
        when(validator.buscarVinculo(23L, 1)).thenReturn(vinculo);

        service.marcarRevisao(1L, 23L, 1);

        assertThat(vinculo.isMarcadaRevisao()).isTrue();
        verify(sessaoQuestaoRepository).save(vinculo);
    }

    @Test
    @DisplayName("marcarRevisao → sessão não encontrada → RecursoNaoEncontradoException")
    void marcarRevisao_sessaoInexistente() {
        when(validator.buscarSessaoDoUsuario(23L, 1L))
                .thenThrow(new RecursoNaoEncontradoException("Sessão não encontrada."));

        assertThatThrownBy(() -> service.marcarRevisao(1L, 23L, 1))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Sessão não encontrada");
    }

    @Test
    @DisplayName("marcarRevisao → sessão não está em andamento → ConflitoDeEstadoException")
    void marcarRevisao_sessaoAbandonada() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(validator.buscarSessaoDoUsuario(23L, 1L)).thenReturn(sessao);
        doThrow(new ConflitoDeEstadoException("Sessão não está em andamento."))
                .when(validator).validarSessaoEmAndamento(sessao);

        assertThatThrownBy(() -> service.marcarRevisao(1L, 23L, 1))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("não está em andamento");
    }

    @Test
    @DisplayName("marcarRevisao → questão não pertence à sessão → RecursoNaoEncontradoException")
    void marcarRevisao_questaoForaDaSessao() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(validator.buscarSessaoDoUsuario(23L, 1L)).thenReturn(sessao);
        when(validator.buscarVinculo(23L, 999))
                .thenThrow(new RecursoNaoEncontradoException("não pertence à sessão"));

        assertThatThrownBy(() -> service.marcarRevisao(1L, 23L, 999))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("não pertence à sessão");
    }

    @Test
    @DisplayName("desmarcarRevisao → desmarca vínculo de sessão ativa")
    void desmarcarRevisao_sucesso() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        SimuladoSessaoQuestao vinculo = vinculo(23L, 1, true);

        when(validator.buscarSessaoDoUsuario(23L, 1L)).thenReturn(sessao);
        when(validator.buscarVinculo(23L, 1)).thenReturn(vinculo);

        service.desmarcarRevisao(1L, 23L, 1);

        assertThat(vinculo.isMarcadaRevisao()).isFalse();
        verify(sessaoQuestaoRepository).save(vinculo);
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private SimuladoSessao sessaoEmAndamento(Long id, Long usuarioId) {
        SimuladoSessao sessao = SimuladoSessao.iniciar(usuarioId, 40, 18000);
        ReflectionTestUtils.setField(sessao, "id", id);
        return sessao;
    }

    private SimuladoSessaoQuestao vinculo(Long sessaoId, int questaoId, boolean marcada) {
        SimuladoSessaoQuestao v = new SimuladoSessaoQuestao(sessaoId, questaoId, 1);
        ReflectionTestUtils.setField(v, "id", 1L);
        if (marcada) v.marcarRevisao();
        return v;
    }
}