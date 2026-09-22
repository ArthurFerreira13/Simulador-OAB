package com.exame.ordem.simulados.service;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.GabaritoResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.questoes.dto.QuestaoRevisadaDTO;
import com.exame.ordem.simulados.SimuladoService;
import com.exame.ordem.simulados.dto.*;
import com.exame.ordem.simulados.internal.*;
import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.LimiteExcedidoException;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
class SimuladoServiceImpl implements SimuladoService {

    private static final int TEMPO_LIMITE_SEGUNDOS = 5 * 60 * 60;
    private static final BigDecimal NOTA_CORTE = BigDecimal.valueOf(50);
    private static final int DIAS_TESTE_GRATIS = 7;

    private final SimuladoSessaoRepository sessaoRepository;
    private final SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;
    private final RespostaSimuladoRepository respostaRepository;
    private final ResultadoSimuladoRepository resultadoRepository;
    private final QuestoesQueryService questoesQueryService;
    private final UsuarioService usuarioService;
    private final SessaoAbandonoHelper sessaoAbandonoHelper;

    SimuladoServiceImpl(SimuladoSessaoRepository sessaoRepository,
                        SimuladoSessaoQuestaoRepository sessaoQuestaoRepository,
                        RespostaSimuladoRepository respostaRepository,
                        ResultadoSimuladoRepository resultadoRepository,
                        QuestoesQueryService questoesQueryService,
                        UsuarioService usuarioService,
                        SessaoAbandonoHelper sessaoAbandonoHelper) {
        this.sessaoRepository = sessaoRepository;
        this.sessaoQuestaoRepository = sessaoQuestaoRepository;
        this.respostaRepository = respostaRepository;
        this.resultadoRepository = resultadoRepository;
        this.questoesQueryService = questoesQueryService;
        this.usuarioService = usuarioService;
        this.sessaoAbandonoHelper = sessaoAbandonoHelper;
    }

    @Override
    @Transactional
    public SessaoDTO iniciarSimulado(IniciarSimuladoRequest request) {
        UsuarioResumo usuario = usuarioService.consultarPerfil(request.email(), request.senha());

        sessaoRepository.findByUsuarioIdAndStatus(usuario.id(), StatusSessao.EM_ANDAMENTO)
                .ifPresent(s -> {
                    if (s.tempoExpirado()) {
                        sessaoAbandonoHelper.abandonarSeExpirada(s.getId());
                    } else {
                        throw new ConflitoDeEstadoException("Já existe simulado em andamento.");
                    }
                });

        verificarLimiteDeUso(usuario);

        List<QuestaoResumo> questoes = questoesQueryService.buscarQuestoesPorProva(request.numeroProva());

        SimuladoSessao sessao = SimuladoSessao.iniciar(usuario.id(), questoes.get(0).provaId(), TEMPO_LIMITE_SEGUNDOS);
        sessao = sessaoRepository.save(sessao);

        int ordem = 1;
        for (QuestaoResumo q : questoes) {
            sessaoQuestaoRepository.save(new SimuladoSessaoQuestao(sessao.getId(), q.id(), ordem++));
        }

        return montarSessaoDTO(sessao, questoes, Map.of());
    }

    private void verificarLimiteDeUso(UsuarioResumo usuario) {
        LocalDateTime fimDoTeste = usuario.criadoEm().plusDays(DIAS_TESTE_GRATIS);
        boolean dentroDoTeste = LocalDateTime.now(ZoneOffset.UTC).isBefore(fimDoTeste);
        if (dentroDoTeste) {
            return;
        }

        LocalDateTime inicioDoDia = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        long simuladosHoje = sessaoRepository.countByUsuarioIdAndIniciadoEmAfter(usuario.id(), inicioDoDia);
        if (simuladosHoje >= 1) {
            throw new LimiteExcedidoException(
                    "Limite diário de simulados atingido. Assine um plano para acesso ilimitado.");
        }
    }

    @Override
    @Transactional
    public void responderQuestao(Long sessaoId, int questaoId, ResponderQuestaoRequest request) {
        SimuladoSessao sessao = sessaoRepository.findById(sessaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão não encontrada."));

        if (sessao.getStatus() != StatusSessao.EM_ANDAMENTO) {
            throw new ConflitoDeEstadoException("Sessão não está em andamento.");
        }

        if (sessao.tempoExpirado()) {
            sessaoAbandonoHelper.abandonarSeExpirada(sessao.getId());
            throw new ConflitoDeEstadoException("Tempo da sessão expirado. Sessão marcada como abandonada.");
        }

        if (!sessaoQuestaoRepository.existsBySessaoIdAndQuestaoId(sessaoId, questaoId)) {
            throw new RecursoNaoEncontradoException(
                    "Questão " + questaoId + " não pertence à sessão " + sessaoId + ".");
        }

        if (!questoesQueryService.existeAlternativaNaQuestao(questaoId, request.alternativaId())) {
            throw new RecursoNaoEncontradoException(
                    "Alternativa " + request.alternativaId() + " não pertence à questão " + questaoId + ".");
        }

        respostaRepository.findBySessaoIdAndQuestaoId(sessaoId, questaoId)
                .ifPresentOrElse(
                        r -> r.atualizarResposta(request.alternativaId()),
                        () -> respostaRepository.save(new RespostaSimulado(sessaoId, questaoId, request.alternativaId()))
                );
    }

    @Override
    @Transactional
    public ResultadoDTO finalizarSimulado(Long sessaoId) {
        SimuladoSessao sessao = sessaoRepository.findById(sessaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão não encontrada."));

        if (sessao.getStatus() != StatusSessao.EM_ANDAMENTO) {
            throw new ConflitoDeEstadoException("Esta sessão já foi finalizada.");
        }

        List<SimuladoSessaoQuestao> questoesDaSessao = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessaoId);
        List<Integer> questaoIds = questoesDaSessao.stream().map(SimuladoSessaoQuestao::getQuestaoId).toList();

        Map<Integer, Long> gabarito = new HashMap<>();
        questoesQueryService.buscarGabarito(questaoIds)
                .forEach(g -> gabarito.put(g.questaoId(), g.alternativaCorretaId()));

        Map<Integer, Long> respostas = respostaRepository.findBySessaoId(sessaoId).stream()
                .collect(Collectors.toMap(RespostaSimulado::getQuestaoId, RespostaSimulado::getAlternativaId));

        int acertos = 0, erros = 0, brancos = 0;
        for (Integer qId : questaoIds) {
            Long respondida = respostas.get(qId);
            Long correta = gabarito.get(qId);
            if (respondida == null) {
                brancos++;
            } else if (correta != null && correta.equals(respondida)) {
                acertos++;
            } else {
                erros++;
            }
        }

        int total = questaoIds.size();
        BigDecimal percentual = BigDecimal.valueOf(acertos)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        boolean aprovado = percentual.compareTo(NOTA_CORTE) >= 0;

        sessao.finalizar();
        sessaoRepository.save(sessao);

        ResultadoSimulado resultado = new ResultadoSimulado(
                sessaoId, total, acertos, erros, brancos, percentual, aprovado, (int) sessao.tempoGastoSegundos());
        resultadoRepository.save(resultado);

        List<QuestaoRevisadaDTO> revisao = montarRevisao(questaoIds, respostas);
        return toResultadoDTO(resultado, revisao);
    }

    @Override
    @Transactional(readOnly = true)
    public SessaoDTO consultarSessaoAtiva(String email, String senha) {
        UsuarioResumo usuario = usuarioService.consultarPerfil(email, senha);

        SimuladoSessao sessao = sessaoRepository.findByUsuarioIdAndStatus(usuario.id(), StatusSessao.EM_ANDAMENTO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nenhum simulado em andamento."));

        if (sessao.tempoExpirado()) {
            sessaoAbandonoHelper.abandonarSeExpirada(sessao.getId());
            throw new RecursoNaoEncontradoException("Nenhum simulado em andamento.");
        }

        List<SimuladoSessaoQuestao> vinculos = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessao.getId());
        List<Integer> questaoIds = vinculos.stream().map(SimuladoSessaoQuestao::getQuestaoId).toList();
        List<QuestaoResumo> questoes = questoesQueryService.buscarPorIds(questaoIds);

        Map<Integer, Long> respostas = respostaRepository.findBySessaoId(sessao.getId()).stream()
                .collect(Collectors.toMap(RespostaSimulado::getQuestaoId, RespostaSimulado::getAlternativaId));

        return montarSessaoDTO(sessao, questoes, respostas);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoDTO consultarResultado(Long sessaoId) {
        ResultadoSimulado resultado = resultadoRepository.findBySessaoId(sessaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Resultado ainda não calculado."));

        List<Integer> questaoIds = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessaoId).stream()
                .map(SimuladoSessaoQuestao::getQuestaoId)
                .toList();

        Map<Integer, Long> respostas = respostaRepository.findBySessaoId(sessaoId).stream()
                .collect(Collectors.toMap(RespostaSimulado::getQuestaoId, RespostaSimulado::getAlternativaId));

        return toResultadoDTO(resultado, montarRevisao(questaoIds, respostas));
    }

    private SessaoDTO montarSessaoDTO(SimuladoSessao sessao,
                                      List<QuestaoResumo> questoes,
                                      Map<Integer, Long> respostas) {
        Map<Integer, Boolean> revisaoPorQuestao = sessaoQuestaoRepository
                .findBySessaoIdOrderByOrdemAsc(sessao.getId()).stream()
                .collect(Collectors.toMap(
                        SimuladoSessaoQuestao::getQuestaoId,
                        SimuladoSessaoQuestao::isMarcadaRevisao));

        List<QuestaoSessaoDTO> dto = questoes.stream()
                .map(q -> new QuestaoSessaoDTO(
                        q.id(), q.numero(), q.enunciado(), q.anulada(), q.disciplina(),
                        q.alternativas().stream()
                                .map(a -> new AlternativaSessaoDTO(a.id(), a.letra(), a.texto()))
                                .toList(),
                        respostas.get(q.id()),
                        revisaoPorQuestao.getOrDefault(q.id(), false)))
                .toList();
        return new SessaoDTO(sessao.getId(), sessao.getStatus().name(),
                sessao.getTempoLimiteSegundos(), sessao.getIniciadoEm(), dto);
    }

    private List<QuestaoRevisadaDTO> montarRevisao(List<Integer> questaoIds, Map<Integer, Long> respostas) {
        return questoesQueryService.buscarComGabarito(questaoIds).stream()
                .map(q -> {
                    Long escolhida = respostas.get(q.questaoId());
                    boolean correta = q.alternativas().stream()
                            .anyMatch(a -> a.ehCorreta() && a.id().equals(escolhida));
                    return new QuestaoRevisadaDTO(q, escolhida, correta);
                })
                .toList();
    }

    // ================================================================
    //  RF03.3 — Marcar / desmarcar questão para revisão
    // ================================================================

    @Override
    @Transactional
    public void marcarRevisao(Long sessaoId, int questaoId, Long usuarioId) {
        SimuladoSessao sessao = buscarSessaoDoUsuario(sessaoId, usuarioId);
        validarSessaoEmAndamento(sessao);

        SimuladoSessaoQuestao vinculo = buscarVinculo(sessaoId, questaoId);
        vinculo.marcarRevisao();
        sessaoQuestaoRepository.save(vinculo);
    }

    @Override
    @Transactional
    public void desmarcarRevisao(Long sessaoId, int questaoId, Long usuarioId) {
        SimuladoSessao sessao = buscarSessaoDoUsuario(sessaoId, usuarioId);
        validarSessaoEmAndamento(sessao);

        SimuladoSessaoQuestao vinculo = buscarVinculo(sessaoId, questaoId);
        vinculo.desmarcarRevisao();
        sessaoQuestaoRepository.save(vinculo);
    }

    // ================================================================
    //  RF03.6 — Abandono manual
    // ================================================================

    @Override
    @Transactional
    public void abandonarSimulado(Long sessaoId, Long usuarioId) {
        SimuladoSessao sessao = buscarSessaoDoUsuario(sessaoId, usuarioId);
        validarSessaoEmAndamento(sessao);

        sessao.abandonar();
        sessaoRepository.save(sessao);
    }

    // ================================================================
    //  Helpers privados
    // ================================================================

    private SimuladoSessao buscarSessaoDoUsuario(Long sessaoId, Long usuarioId) {
        return sessaoRepository.findByIdAndUsuarioId(sessaoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão não encontrada."));
    }

    private void validarSessaoEmAndamento(SimuladoSessao sessao) {
        if (sessao.getStatus() != StatusSessao.EM_ANDAMENTO) {
            throw new ConflitoDeEstadoException("Sessão não está em andamento.");
        }
        if (sessao.tempoExpirado()) {
            sessaoAbandonoHelper.abandonarSeExpirada(sessao.getId());
            throw new ConflitoDeEstadoException("Tempo da sessão expirado. Sessão marcada como abandonada.");
        }
    }

    private SimuladoSessaoQuestao buscarVinculo(Long sessaoId, int questaoId) {
        return sessaoQuestaoRepository.findBySessaoIdAndQuestaoId(sessaoId, questaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Questão " + questaoId + " não pertence à sessão " + sessaoId + "."));
    }

    private ResultadoDTO toResultadoDTO(ResultadoSimulado r, List<QuestaoRevisadaDTO> revisao) {
        return new ResultadoDTO(r.getTotalQuestoes(), r.getAcertos(), r.getErros(), r.getBrancos(),
                r.getPercentualAcerto(), r.isAprovado(), r.getTempoGastoSegundos(), revisao);
    }
}