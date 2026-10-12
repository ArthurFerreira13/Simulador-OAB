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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

@Service
class SimuladoServiceImpl implements SimuladoService {

    private static final Logger log = LoggerFactory.getLogger(SimuladoServiceImpl.class);

    private enum Categoria {
        ACERTO,
        ERRO,
        BRANCO
    }

    private final int tempoLimiteSegundos;
    private final BigDecimal notaCortePercentual;
    private final int limiteDiario;
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
                        @Value("${app.simulado.tempo-limite-segundos:18000}") int tempoLimiteSegundos,
                        @Value("${app.simulado.nota-corte-percentual:50}") int notaCortePercentual,
                        @Value("${app.simulado.limite-diario:1}") int limiteDiario) {
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
        this.tempoLimiteSegundos = tempoLimiteSegundos;
        this.notaCortePercentual = BigDecimal.valueOf(notaCortePercentual);
        this.limiteDiario = limiteDiario;
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
                usuarioId, questoes.get(0).provaId(), tempoLimiteSegundos);
        sessao = sessaoRepository.save(sessao);

        int ordem = 1;
        for (QuestaoResumo q : questoes) {
            sessaoQuestaoRepository.save(new SimuladoSessaoQuestao(sessao.getId(), q.id(), ordem++));
        }

        return mapper.toSessaoDTO(sessao, questoes, Map.of());
    }

    /**
     * Limite diário (RN14, D13, D30).
     *
     * <p>Regras:
     * <ul>
     *   <li>{@code limiteDiario <= 0} desliga o limite (dev).</li>
     *   <li>"Dia" é o dia civil em {@code America/Sao_Paulo} (D30).</li>
     *   <li>Sessão abandonada sem nenhuma resposta não conta (D13).</li>
     * </ul>
     */
    private void verificarLimiteDiario(Long usuarioId) {
        if (limiteDiario <= 0) {
            return;
        }

        ZoneId fuso = clock.getZone();
        Instant inicioDoDiaSP = LocalDate.now(clock).atStartOfDay(fuso).toInstant();
        LocalDateTime inicioDoDiaUTC = LocalDateTime.ofInstant(inicioDoDiaSP, ZoneOffset.UTC);

        long simuladosHoje = sessaoRepository
                .countComRespostaByUsuarioIdAndIniciadoEmAfter(usuarioId, inicioDoDiaUTC);

        if (simuladosHoje >= limiteDiario) {
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

        // Idempotência: já finalizada → retorna resultado existente
        if (sessao.getStatus() == StatusSessao.CONCLUIDO) {
            return retornarResultadoExistente(sessaoId);
        }

        // D31/CA-RF03.2-01: sessão expirada pode ser finalizada normalmente
        // — o candidato é corrigido com as respostas já registradas.
        // Só sessões ABANDONADAS são recusadas.
        if (sessao.getStatus() != StatusSessao.EM_ANDAMENTO) {
            throw new ConflitoDeEstadoException("Sessão não está em andamento.");
        }

        List<Integer> questaoIds = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessaoId).stream()
                .map(SimuladoSessaoQuestao::getQuestaoId)
                .toList();

        Map<Integer, Long> gabarito = new HashMap<>();
        gabaritoService.buscarGabarito(questaoIds)
                .forEach(g -> gabarito.put(g.questaoId(), g.alternativaCorretaId()));

        Map<Integer, Long> respostas = respostaRepository.findBySessaoId(sessaoId).stream()
                .collect(Collectors.toMap(RespostaSimulado::getQuestaoId, RespostaSimulado::getAlternativaId));

        Map<Categoria, Long> contagem = questaoIds.stream()
                .collect(Collectors.groupingBy(
                        qId -> categorizar(qId, respostas, gabarito),
                        Collectors.counting()));

        int acertos = contagem.getOrDefault(Categoria.ACERTO, 0L).intValue();
        int erros   = contagem.getOrDefault(Categoria.ERRO,   0L).intValue();
        int brancos = contagem.getOrDefault(Categoria.BRANCO, 0L).intValue();

        int total = questaoIds.size();
        BigDecimal percentual = BigDecimal.valueOf(acertos)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        boolean aprovado = percentual.compareTo(notaCortePercentual) >= 0;

        sessao.finalizar();
        sessaoRepository.save(sessao);

        ResultadoSimulado resultado = new ResultadoSimulado(
                sessaoId, total, acertos, erros, brancos, percentual, aprovado,
                (int) sessao.tempoGastoSegundos());
        resultadoRepository.save(resultado);

        return mapper.toResultadoDTO(resultado, questaoIds, respostas);
    }

    private Categoria categorizar(Integer qId,
                                  Map<Integer, Long> respostas,
                                  Map<Integer, Long> gabarito) {

        Long correta = gabarito.get(qId);
        if (correta == null) {
            // D03: questões anuladas não aparecem para o candidato e,
            // portanto, não deveriam chegar aqui. Se chegarem, é sinal
            // de dado inconsistente — contar como branco e logar.
            log.debug("Questão {} sem gabarito (anulada) — contada como branco", qId);
            return Categoria.BRANCO;
        }

        Long respondida = respostas.get(qId);
        if (respondida == null) {
            return Categoria.BRANCO;
        }

        return correta.equals(respondida) ? Categoria.ACERTO : Categoria.ERRO;
    }

    private ResultadoDTO retornarResultadoExistente(Long sessaoId) {
        ResultadoSimulado existente = resultadoRepository.findBySessaoId(sessaoId)
                .orElseThrow(() -> new ConflitoDeEstadoException(
                        "Sessão concluída sem resultado persistido. Contate o suporte."));

        List<Integer> questaoIds = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessaoId).stream()
                .map(SimuladoSessaoQuestao::getQuestaoId)
                .toList();

        Map<Integer, Long> respostas = respostaRepository.findBySessaoId(sessaoId).stream()
                .collect(Collectors.toMap(RespostaSimulado::getQuestaoId, RespostaSimulado::getAlternativaId));

        return mapper.toResultadoDTO(existente, questaoIds, respostas);
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