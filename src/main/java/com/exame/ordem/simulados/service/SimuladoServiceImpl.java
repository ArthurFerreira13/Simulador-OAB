package com.exame.ordem.simulados.service;

import com.exame.ordem.questoes.GabaritoService;
import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.AlternativaResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.simulados.SimuladoService;
import com.exame.ordem.simulados.dto.*;
import com.exame.ordem.simulados.internal.*;
import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.LimiteExcedidoException;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import com.exame.ordem.usuarios.PoliticaAcessoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
class SimuladoServiceImpl implements SimuladoService {

    private static final Logger log = LoggerFactory.getLogger(SimuladoServiceImpl.class);

    private enum Categoria { ACERTO, ERRO, BRANCO }

    private final int tempoLimiteSegundos;
    private final double notaCortePercentual;
    private final Clock clock;

    private final SimuladoSessaoRepository sessaoRepository;
    private final SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;
    private final RespostaSimuladoRepository respostaRepository;
    private final ResultadoSimuladoRepository resultadoRepository;
    private final QuestoesQueryService questoesQueryService;
    private final GabaritoService gabaritoService;
    private final SessaoAbandonoHelper sessaoAbandonoHelper;
    private final SimuladoSessaoValidator validator;
    private final SimuladoMapper mapper;
    private final PoliticaAcessoService politicaAcesso;

    SimuladoServiceImpl(SimuladoSessaoRepository sessaoRepository,
                        SimuladoSessaoQuestaoRepository sessaoQuestaoRepository,
                        RespostaSimuladoRepository respostaRepository,
                        ResultadoSimuladoRepository resultadoRepository,
                        QuestoesQueryService questoesQueryService,
                        GabaritoService gabaritoService,
                        SessaoAbandonoHelper sessaoAbandonoHelper,
                        SimuladoSessaoValidator validator,
                        SimuladoMapper mapper,
                        Clock clock,
                        PoliticaAcessoService politicaAcesso,
                        @Value("${app.simulado.tempo-limite-segundos:18000}") int tempoLimiteSegundos,
                        @Value("${app.simulado.nota-corte-percentual:50}") double notaCortePercentual) {
        this.sessaoRepository = sessaoRepository;
        this.sessaoQuestaoRepository = sessaoQuestaoRepository;
        this.respostaRepository = respostaRepository;
        this.resultadoRepository = resultadoRepository;
        this.questoesQueryService = questoesQueryService;
        this.gabaritoService = gabaritoService;
        this.sessaoAbandonoHelper = sessaoAbandonoHelper;
        this.validator = validator;
        this.mapper = mapper;
        this.clock = clock;
        this.politicaAcesso = politicaAcesso;
        this.tempoLimiteSegundos = tempoLimiteSegundos;
        this.notaCortePercentual = notaCortePercentual;
    }

    // ================================================================
    //  Iniciar simulado
    // ================================================================

    @Override
    @Transactional
    public SessaoDTO iniciarSimulado(Long usuarioId, IniciarSimuladoRequest request) {
        sessaoRepository.findByUsuarioIdAndStatus(usuarioId, StatusSessao.EM_ANDAMENTO)
                .ifPresent(s -> {
                    if (s.tempoExpirado()) {
                        sessaoAbandonoHelper.abandonarSeExpirada(s.getId());
                    } else {
                        throw new ConflitoDeEstadoException("Já existe simulado em andamento.");
                    }
                });

        verificarLimiteDiario(usuarioId);

        List<QuestaoResumo> questoes = questoesQueryService.buscarQuestoesPorProva(request.numeroProva());
        if (questoes.isEmpty()) {
            throw new RecursoNaoEncontradoException("Prova sem questões.");
        }

        SimuladoSessao sessao = SimuladoSessao.iniciar(
                usuarioId, questoes.getFirst().provaId(), tempoLimiteSegundos);
        sessao = sessaoRepository.save(sessao);

        final Long sessaoId = sessao.getId();
        List<SimuladoSessaoQuestao> vinculos = IntStream.range(0, questoes.size())
                .mapToObj(i -> new SimuladoSessaoQuestao(sessaoId, questoes.get(i).id(), i + 1))
                .toList();
        sessaoQuestaoRepository.saveAll(vinculos);

        return mapper.toSessaoDTO(sessao, questoes, Map.of());
    }

    private void verificarLimiteDiario(Long usuarioId) {
        int limite = politicaAcesso.limiteDiarioSimulado(usuarioId);
        if (limite <= 0) {
            return;
        }

        ZoneId fuso = clock.getZone();
        Instant inicioDoDiaSP = LocalDate.now(clock).atStartOfDay(fuso).toInstant();
        LocalDateTime inicioDoDiaUTC = LocalDateTime.ofInstant(inicioDoDiaSP, ZoneOffset.UTC);

        long simuladosHoje = sessaoRepository
                .countComRespostaByUsuarioIdAndIniciadoEmAfter(usuarioId, inicioDoDiaUTC);

        if (simuladosHoje >= limite) {
            throw new LimiteExcedidoException(
                    "Limite diário de simulados atingido. Assine um plano para acesso ilimitado.");
        }
    }

    // ================================================================
    //  Responder questão
    // ================================================================

    @Override
    @Transactional
    public void responderQuestao(Long usuarioId, Long sessaoId, int questaoId, ResponderQuestaoRequest request) {
        SimuladoSessao sessao = validator.buscarSessaoDoUsuario(sessaoId, usuarioId);
        validator.validarSessaoEmAndamento(sessao);

        if (!sessaoQuestaoRepository.existsBySessaoIdAndQuestaoId(sessaoId, questaoId)) {
            throw new RecursoNaoEncontradoException(
                    "Questão " + questaoId + " não pertence à sessão " + sessaoId + ".");
        }

        QuestaoResumo questao = questoesQueryService.buscarQuestaoPorId(questaoId);
        Long alternativaId = questao.alternativas().stream()
                .filter(a -> a.letra().equals(request.alternativa()))
                .map(AlternativaResumo::id)
                .findFirst()
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Alternativa " + request.alternativa() + " não existe na questão " + questaoId));

        respostaRepository.findBySessaoIdAndQuestaoId(sessaoId, questaoId)
                .ifPresentOrElse(
                        r -> r.atualizarResposta(alternativaId),
                        () -> respostaRepository.save(
                                new RespostaSimulado(sessaoId, questaoId, alternativaId))
                );
    }

    // ================================================================
    //  Finalizar simulado
    // ================================================================

    @Override
    @Transactional
    public ResultadoDTO finalizarSimulado(Long usuarioId, Long sessaoId) {
        SimuladoSessao sessao = validator.buscarSessaoDoUsuario(sessaoId, usuarioId);

        if (sessao.getStatus() == StatusSessao.CONCLUIDO) {
            return retornarResultadoExistente(sessaoId);
        }
        if (sessao.getStatus() != StatusSessao.EM_ANDAMENTO) {
            throw new ConflitoDeEstadoException("Sessão não está em andamento.");
        }

        ContextoSimulado ctx = carregarContexto(sessaoId);

        Map<Integer, Long> gabarito = new HashMap<>();
        gabaritoService.buscarGabarito(ctx.questaoIds())
                .forEach(g -> gabarito.put(g.questaoId(), g.alternativaCorretaId()));

        Map<Categoria, Long> contagem = ctx.questaoIds().stream()
                .collect(Collectors.groupingBy(
                        qId -> categorizar(qId, ctx.respostas(), gabarito),
                        Collectors.counting()));

        int acertos = contagem.getOrDefault(Categoria.ACERTO, 0L).intValue();
        int erros   = contagem.getOrDefault(Categoria.ERRO,   0L).intValue();
        int brancos = contagem.getOrDefault(Categoria.BRANCO, 0L).intValue();
        int total   = ctx.questaoIds().size();

        double percentual = calcularPercentual(acertos, total);
        boolean aprovado = percentual >= notaCortePercentual;

        sessao.finalizar();
        sessaoRepository.save(sessao);

        ResultadoSimulado resultado = new ResultadoSimulado(
                sessaoId, total, acertos, erros, brancos,
                BigDecimal.valueOf(percentual),
                aprovado,
                (int) sessao.tempoGastoSegundos());
        resultadoRepository.save(resultado);

        return mapper.toResultadoDTO(resultado, ctx.questaoIds(), ctx.respostas());
    }

    private ResultadoDTO retornarResultadoExistente(Long sessaoId) {
        ResultadoSimulado existente = resultadoRepository.findBySessaoId(sessaoId)
                .orElseThrow(() -> new ConflitoDeEstadoException(
                        "Sessão concluída sem resultado persistido. Acione o suporte."));

        ContextoSimulado ctx = carregarContexto(sessaoId);
        return mapper.toResultadoDTO(existente, ctx.questaoIds(), ctx.respostas());
    }

    /**
     * Carrega os IDs das questões da sessão e o mapa de respostas.
     * Extraído para eliminar a duplicação entre {@link #finalizarSimulado}
     * e {@link #retornarResultadoExistente}.
     */
    private ContextoSimulado carregarContexto(Long sessaoId) {
        List<Integer> questaoIds = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessaoId).stream()
                .map(SimuladoSessaoQuestao::getQuestaoId)
                .toList();

        Map<Integer, Long> respostas = respostaRepository.findBySessaoId(sessaoId).stream()
                .collect(Collectors.toMap(RespostaSimulado::getQuestaoId, RespostaSimulado::getAlternativaId));

        return new ContextoSimulado(questaoIds, respostas);
    }

    private record ContextoSimulado(List<Integer> questaoIds, Map<Integer, Long> respostas) {}

    /**
     * Percentual com 2 casas decimais, arredondado para o mais próximo.
     * Ex.: 33 acertos em 80 questões → 41.25
     */
    private double calcularPercentual(int acertos, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((acertos * 10_000.0) / total) / 100.0;
    }

    /**
     * Classifica uma questão em ACERTO, ERRO ou BRANCO (D03/RN04).
     * Anuladas não chegam aqui (filtradas na query); se chegarem, é
     * sinal de dado inconsistente — conta como BRANCO e loga.
     */
    private Categoria categorizar(Integer qId,
                                  Map<Integer, Long> respostas,
                                  Map<Integer, Long> gabarito) {
        Long correta = gabarito.get(qId);
        if (correta == null) {
            log.debug("Questão {} sem gabarito (anulada) — contada como branco", qId);
            return Categoria.BRANCO;
        }
        Long respondida = respostas.get(qId);
        if (respondida == null) {
            return Categoria.BRANCO;
        }
        return correta.equals(respondida) ? Categoria.ACERTO : Categoria.ERRO;
    }

    // ================================================================
    //  Abandonar simulado
    // ================================================================

    @Override
    @Transactional
    public void abandonarSimulado(Long usuarioId, Long sessaoId) {
        SimuladoSessao sessao = validator.buscarSessaoDoUsuario(sessaoId, usuarioId);
        validator.validarSessaoEmAndamento(sessao);

        sessao.abandonar();
        sessaoRepository.save(sessao);
    }
}