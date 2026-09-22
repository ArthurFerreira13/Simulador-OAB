package com.exame.ordem.simulados.service;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.GabaritoResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.simulados.dto.*;
import com.exame.ordem.simulados.internal.*;
import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.LimiteExcedidoException;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimuladoServiceImplTest {

    @Mock SimuladoSessaoRepository sessaoRepository;
    @Mock SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;
    @Mock RespostaSimuladoRepository respostaRepository;
    @Mock ResultadoSimuladoRepository resultadoRepository;
    @Mock QuestoesQueryService questoesQueryService;
    @Mock UsuarioService usuarioService;
    @Mock SessaoAbandonoHelper sessaoAbandonoHelper;

    @InjectMocks SimuladoServiceImpl service;

    // ================================================================
    //  marcarRevisao
    // ================================================================

    @Test
    @DisplayName("marcarRevisao → marca vínculo de sessão ativa")
    void marcarRevisao_sucesso() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        SimuladoSessaoQuestao vinculo = vinculo(23L, 1, false);

        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.of(sessao));
        when(sessaoQuestaoRepository.findBySessaoIdAndQuestaoId(23L, 1)).thenReturn(Optional.of(vinculo));

        service.marcarRevisao(23L, 1, 1L);

        assertThat(vinculo.isMarcadaRevisao()).isTrue();
        verify(sessaoQuestaoRepository).save(vinculo);
    }

    @Test
    @DisplayName("marcarRevisao → sessão não encontrada → RecursoNaoEncontradoException")
    void marcarRevisao_sessaoInexistente() {
        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarRevisao(23L, 1, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Sessão não encontrada");

        verifyNoInteractions(sessaoQuestaoRepository);
    }

    @Test
    @DisplayName("marcarRevisao → sessão não está em andamento → ConflitoDeEstadoException")
    void marcarRevisao_sessaoAbandonada() {
        SimuladoSessao sessao = sessaoAbandonada(23L, 1L);
        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> service.marcarRevisao(23L, 1, 1L))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("não está em andamento");

        verifyNoInteractions(sessaoQuestaoRepository);
    }

    @Test
    @DisplayName("marcarRevisao → questão não pertence à sessão → RecursoNaoEncontradoException")
    void marcarRevisao_questaoForaDaSessao() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.of(sessao));
        when(sessaoQuestaoRepository.findBySessaoIdAndQuestaoId(23L, 999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarRevisao(23L, 999, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("não pertence à sessão");
    }

    // ================================================================
    //  desmarcarRevisao
    // ================================================================

    @Test
    @DisplayName("desmarcarRevisao → desmarca vínculo de sessão ativa")
    void desmarcarRevisao_sucesso() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        SimuladoSessaoQuestao vinculo = vinculo(23L, 1, true);

        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.of(sessao));
        when(sessaoQuestaoRepository.findBySessaoIdAndQuestaoId(23L, 1)).thenReturn(Optional.of(vinculo));

        service.desmarcarRevisao(23L, 1, 1L);

        assertThat(vinculo.isMarcadaRevisao()).isFalse();
        verify(sessaoQuestaoRepository).save(vinculo);
    }

    @Test
    @DisplayName("desmarcarRevisao → sessão não encontrada → RecursoNaoEncontradoException")
    void desmarcarRevisao_sessaoInexistente() {
        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desmarcarRevisao(23L, 1, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Sessão não encontrada");
    }

    @Test
    @DisplayName("desmarcarRevisao → sessão abandonada → ConflitoDeEstadoException")
    void desmarcarRevisao_sessaoAbandonada() {
        SimuladoSessao sessao = sessaoAbandonada(23L, 1L);
        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> service.desmarcarRevisao(23L, 1, 1L))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("não está em andamento");
    }

    @Test
    @DisplayName("desmarcarRevisao → questão não pertence à sessão → RecursoNaoEncontradoException")
    void desmarcarRevisao_questaoForaDaSessao() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.of(sessao));
        when(sessaoQuestaoRepository.findBySessaoIdAndQuestaoId(23L, 999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desmarcarRevisao(23L, 999, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("não pertence à sessão");
    }

    // ================================================================
    //  abandonarSimulado
    // ================================================================

    @Test
    @DisplayName("abandonarSimulado → marca sessão como ABANDONADO")
    void abandonarSimulado_sucesso() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.of(sessao));

        service.abandonarSimulado(23L, 1L);

        assertThat(sessao.getStatus()).isEqualTo(StatusSessao.ABANDONADO);
        assertThat(sessao.getFinalizadoEm()).isNotNull();
        verify(sessaoRepository).save(sessao);
    }

    @Test
    @DisplayName("abandonarSimulado → sessão não encontrada → RecursoNaoEncontradoException")
    void abandonarSimulado_sessaoInexistente() {
        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.abandonarSimulado(23L, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Sessão não encontrada");
    }

    @Test
    @DisplayName("abandonarSimulado → já abandonada → ConflitoDeEstadoException")
    void abandonarSimulado_jaAbandonada() {
        SimuladoSessao sessao = sessaoAbandonada(23L, 1L);
        when(sessaoRepository.findByIdAndUsuarioId(23L, 1L)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> service.abandonarSimulado(23L, 1L))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("não está em andamento");
    }

    @Test
    @DisplayName("abandonarSimulado → sessão de outro usuário → RecursoNaoEncontradoException")
    void abandonarSimulado_sessaoDeOutroUsuario() {
        when(sessaoRepository.findByIdAndUsuarioId(23L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.abandonarSimulado(23L, 99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Sessão não encontrada");
    }

    // ================================================================
    //  iniciarSimulado
    // ================================================================

    @Test
    @DisplayName("iniciarSimulado → cria sessão e vínculos para prova válida")
    void iniciarSimulado_sucesso() {
        UsuarioResumo usuario = usuarioResumo(1L);
        when(usuarioService.consultarPerfil("arthur@teste.com", "senha123")).thenReturn(usuario);
        when(sessaoRepository.findByUsuarioIdAndStatus(1L, StatusSessao.EM_ANDAMENTO))
                .thenReturn(Optional.empty());
        SimuladoSessao sessaoSalva = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.save(any(SimuladoSessao.class))).thenReturn(sessaoSalva);

        List<QuestaoResumo> questoes = List.of(questaoResumo(1, 1), questaoResumo(2, 2));
        when(questoesQueryService.buscarQuestoesPorProva(40)).thenReturn(questoes);

        SessaoDTO dto = service.iniciarSimulado(new IniciarSimuladoRequest(
                "arthur@teste.com", "senha123", 40));

        assertThat(dto.sessaoId()).isEqualTo(23L);
        assertThat(dto.status()).isEqualTo("EM_ANDAMENTO");
        assertThat(dto.questoes()).hasSize(2);
        verify(sessaoQuestaoRepository, times(2)).save(any(SimuladoSessaoQuestao.class));
    }

    @Test
    @DisplayName("iniciarSimulado → já existe sessão em andamento → ConflitoDeEstadoException")
    void iniciarSimulado_jaTemSessaoAtiva() {
        UsuarioResumo usuario = usuarioResumo(1L);
        when(usuarioService.consultarPerfil("arthur@teste.com", "senha123")).thenReturn(usuario);
        SimuladoSessao sessaoEmCurso = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.findByUsuarioIdAndStatus(1L, StatusSessao.EM_ANDAMENTO))
                .thenReturn(Optional.of(sessaoEmCurso));

        assertThatThrownBy(() -> service.iniciarSimulado(new IniciarSimuladoRequest(
                "arthur@teste.com", "senha123", 40)))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("Já existe simulado em andamento");

        verify(sessaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("iniciarSimulado → sessão antiga expirada → abandona e prossegue")
    void iniciarSimulado_sessaoExpirada() {
        UsuarioResumo usuario = usuarioResumo(1L);
        when(usuarioService.consultarPerfil("arthur@teste.com", "senha123")).thenReturn(usuario);

        SimuladoSessao expirada = SimuladoSessao.iniciar(1L, 40, 1);
        ReflectionTestUtils.setField(expirada, "id", 20L);
        ReflectionTestUtils.setField(expirada, "iniciadoEm",
                LocalDateTime.now(ZoneOffset.UTC).minusHours(2));
        when(sessaoRepository.findByUsuarioIdAndStatus(1L, StatusSessao.EM_ANDAMENTO))
                .thenReturn(Optional.of(expirada));

        SimuladoSessao nova = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.save(any(SimuladoSessao.class))).thenReturn(nova);
        when(questoesQueryService.buscarQuestoesPorProva(40))
                .thenReturn(List.of(questaoResumo(1, 1)));

        service.iniciarSimulado(new IniciarSimuladoRequest("arthur@teste.com", "senha123", 40));

        verify(sessaoAbandonoHelper).abandonarSeExpirada(20L);
    }

    @Test
    @DisplayName("iniciarSimulado → fora do trial e já usou 1 hoje → LimiteExcedidoException")
    void iniciarSimulado_limiteDiario() {
        UsuarioResumo usuario = usuarioResumoForaDoTrial(1L);
        when(usuarioService.consultarPerfil("arthur@teste.com", "senha123")).thenReturn(usuario);
        when(sessaoRepository.findByUsuarioIdAndStatus(1L, StatusSessao.EM_ANDAMENTO))
                .thenReturn(Optional.empty());
        when(sessaoRepository.countByUsuarioIdAndIniciadoEmAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.iniciarSimulado(new IniciarSimuladoRequest(
                "arthur@teste.com", "senha123", 40)))
                .isInstanceOf(LimiteExcedidoException.class)
                .hasMessageContaining("Limite diário");
    }

    // ================================================================
    //  responderQuestao
    // ================================================================

    @Test
    @DisplayName("responderQuestao → salva nova resposta")
    void responderQuestao_salvaNovaResposta() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.findById(23L)).thenReturn(Optional.of(sessao));
        when(sessaoQuestaoRepository.existsBySessaoIdAndQuestaoId(23L, 1)).thenReturn(true);
        when(questoesQueryService.existeAlternativaNaQuestao(1, 1003L)).thenReturn(true);
        when(respostaRepository.findBySessaoIdAndQuestaoId(23L, 1)).thenReturn(Optional.empty());

        service.responderQuestao(23L, 1, new ResponderQuestaoRequest(1003L));

        verify(respostaRepository).save(any(RespostaSimulado.class));
    }

    @Test
    @DisplayName("responderQuestao → atualiza resposta existente")
    void responderQuestao_atualizaResposta() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.findById(23L)).thenReturn(Optional.of(sessao));
        when(sessaoQuestaoRepository.existsBySessaoIdAndQuestaoId(23L, 1)).thenReturn(true);
        when(questoesQueryService.existeAlternativaNaQuestao(1, 1003L)).thenReturn(true);

        RespostaSimulado existente = new RespostaSimulado(23L, 1, 1001L);
        when(respostaRepository.findBySessaoIdAndQuestaoId(23L, 1))
                .thenReturn(Optional.of(existente));

        service.responderQuestao(23L, 1, new ResponderQuestaoRequest(1003L));

        assertThat(existente.getAlternativaId()).isEqualTo(1003L);
        verify(respostaRepository, never()).save(any());
    }

    @Test
    @DisplayName("responderQuestao → sessão abandonada → ConflitoDeEstadoException")
    void responderQuestao_sessaoAbandonada() {
        SimuladoSessao sessao = sessaoAbandonada(23L, 1L);
        when(sessaoRepository.findById(23L)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> service.responderQuestao(23L, 1,
                new ResponderQuestaoRequest(1003L)))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("não está em andamento");
    }

    @Test
    @DisplayName("responderQuestao → questão não pertence à sessão → RecursoNaoEncontradoException")
    void responderQuestao_questaoForaDaSessao() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.findById(23L)).thenReturn(Optional.of(sessao));
        when(sessaoQuestaoRepository.existsBySessaoIdAndQuestaoId(23L, 999)).thenReturn(false);

        assertThatThrownBy(() -> service.responderQuestao(23L, 999,
                new ResponderQuestaoRequest(1003L)))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("não pertence à sessão");
    }

    @Test
    @DisplayName("responderQuestao → alternativa não pertence à questão → RecursoNaoEncontradoException")
    void responderQuestao_alternativaInvalida() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.findById(23L)).thenReturn(Optional.of(sessao));
        when(sessaoQuestaoRepository.existsBySessaoIdAndQuestaoId(23L, 1)).thenReturn(true);
        when(questoesQueryService.existeAlternativaNaQuestao(1, 9999L)).thenReturn(false);

        assertThatThrownBy(() -> service.responderQuestao(23L, 1,
                new ResponderQuestaoRequest(9999L)))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("não pertence à questão");
    }

    // ================================================================
    //  finalizarSimulado
    // ================================================================

    @Test
    @DisplayName("finalizarSimulado → calcula acertos, erros e brancos")
    void finalizarSimulado_calculaResultado() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.findById(23L)).thenReturn(Optional.of(sessao));

        when(sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(23L)).thenReturn(List.of(
                vinculo(23L, 1, false), vinculo(23L, 2, false), vinculo(23L, 3, false)));

        when(questoesQueryService.buscarGabarito(List.of(1, 2, 3))).thenReturn(List.of(
                new GabaritoResumo(1, 1003L),
                new GabaritoResumo(2, 2003L),
                new GabaritoResumo(3, 3003L)));

        RespostaSimulado r1 = new RespostaSimulado(23L, 1, 1003L);
        RespostaSimulado r2 = new RespostaSimulado(23L, 2, 2001L);
        when(respostaRepository.findBySessaoId(23L)).thenReturn(List.of(r1, r2));

        when(questoesQueryService.buscarComGabarito(List.of(1, 2, 3)))
                .thenReturn(List.of());

        ResultadoDTO dto = service.finalizarSimulado(23L);

        assertThat(dto.totalQuestoes()).isEqualTo(3);
        assertThat(dto.acertos()).isEqualTo(1);
        assertThat(dto.erros()).isEqualTo(1);
        assertThat(dto.brancos()).isEqualTo(1);
        assertThat(sessao.getStatus()).isEqualTo(StatusSessao.CONCLUIDO);
        verify(resultadoRepository).save(any(ResultadoSimulado.class));
    }

    @Test
    @DisplayName("finalizarSimulado → sessão já finalizada → ConflitoDeEstadoException")
    void finalizarSimulado_sessaoJaFinalizada() {
        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        sessao.finalizar();
        when(sessaoRepository.findById(23L)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> service.finalizarSimulado(23L))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("já foi finalizada");
    }

    // ================================================================
    //  consultarSessaoAtiva
    // ================================================================

    @Test
    @DisplayName("consultarSessaoAtiva → retorna sessão do usuário")
    void consultarSessaoAtiva_sucesso() {
        UsuarioResumo usuario = usuarioResumo(1L);
        when(usuarioService.consultarPerfil("arthur@teste.com", "senha123")).thenReturn(usuario);

        SimuladoSessao sessao = sessaoEmAndamento(23L, 1L);
        when(sessaoRepository.findByUsuarioIdAndStatus(1L, StatusSessao.EM_ANDAMENTO))
                .thenReturn(Optional.of(sessao));
        when(sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(23L))
                .thenReturn(List.of(vinculo(23L, 1, false)));
        when(questoesQueryService.buscarPorIds(List.of(1)))
                .thenReturn(List.of(questaoResumo(1, 1)));
        when(respostaRepository.findBySessaoId(23L)).thenReturn(List.of());

        SessaoDTO dto = service.consultarSessaoAtiva("arthur@teste.com", "senha123");

        assertThat(dto.sessaoId()).isEqualTo(23L);
        assertThat(dto.questoes()).hasSize(1);
    }

    @Test
    @DisplayName("consultarSessaoAtiva → sem sessão → RecursoNaoEncontradoException")
    void consultarSessaoAtiva_semSessao() {
        UsuarioResumo usuario = usuarioResumo(1L);
        when(usuarioService.consultarPerfil("arthur@teste.com", "senha123")).thenReturn(usuario);
        when(sessaoRepository.findByUsuarioIdAndStatus(1L, StatusSessao.EM_ANDAMENTO))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarSessaoAtiva("arthur@teste.com", "senha123"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Nenhum simulado em andamento");
    }

    // ================================================================
    //  consultarResultado
    // ================================================================

    @Test
    @DisplayName("consultarResultado → retorna resultado existente")
    void consultarResultado_sucesso() {
        ResultadoSimulado resultado = new ResultadoSimulado(
                23L, 3, 1, 1, 1,
                new BigDecimal("33.33"), false, 3600);
        when(resultadoRepository.findBySessaoId(23L)).thenReturn(Optional.of(resultado));
        when(sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(23L))
                .thenReturn(List.of(vinculo(23L, 1, false)));
        when(respostaRepository.findBySessaoId(23L)).thenReturn(List.of());
        when(questoesQueryService.buscarComGabarito(List.of(1))).thenReturn(List.of());

        ResultadoDTO dto = service.consultarResultado(23L);

        assertThat(dto.totalQuestoes()).isEqualTo(3);
        assertThat(dto.acertos()).isEqualTo(1);
    }

    @Test
    @DisplayName("consultarResultado → resultado ainda não calculado → RecursoNaoEncontradoException")
    void consultarResultado_naoDisponivel() {
        when(resultadoRepository.findBySessaoId(23L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarResultado(23L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("ainda não calculado");
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private SimuladoSessao sessaoEmAndamento(Long id, Long usuarioId) {
        SimuladoSessao sessao = SimuladoSessao.iniciar(usuarioId, 40, 18000);
        ReflectionTestUtils.setField(sessao, "id", id);
        return sessao;
    }

    private SimuladoSessao sessaoAbandonada(Long id, Long usuarioId) {
        SimuladoSessao sessao = sessaoEmAndamento(id, usuarioId);
        sessao.abandonar();
        return sessao;
    }

    private SimuladoSessaoQuestao vinculo(Long sessaoId, int questaoId, boolean marcada) {
        SimuladoSessaoQuestao v = new SimuladoSessaoQuestao(sessaoId, questaoId, 1);
        ReflectionTestUtils.setField(v, "id", 1L);
        if (marcada) v.marcarRevisao();
        return v;
    }

    private UsuarioResumo usuarioResumo(Long id) {
        return new UsuarioResumo(
                id,
                "arthur@teste.com",
                "Arthur",
                null,
                LocalDateTime.now(ZoneOffset.UTC));
    }

    private UsuarioResumo usuarioResumoForaDoTrial(Long id) {
        return new UsuarioResumo(
                id,
                "arthur@teste.com",
                "Arthur",
                null,
                LocalDateTime.now(ZoneOffset.UTC).minusDays(30));
    }

    private QuestaoResumo questaoResumo(int id, int numero) {
        return new QuestaoResumo(
                id, 40, numero,
                "Enunciado " + numero,
                false, "Ética",
                List.of());
    }
}