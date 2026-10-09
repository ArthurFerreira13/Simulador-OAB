package com.exame.ordem.simulados.service;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.simulados.SimuladoService;
import com.exame.ordem.simulados.dto.*;
import com.exame.ordem.simulados.internal.*;
import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.LimiteExcedidoException;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
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

    private final SimuladoSessaoRepository sessaoRepository;
    private final SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;
    private final RespostaSimuladoRepository respostaRepository;
    private final ResultadoSimuladoRepository resultadoRepository;
    private final QuestoesQueryService questoesQueryService;
    private final SessaoAbandonoHelper sessaoAbandonoHelper;
    private final SimuladoSessaoValidator validator;
    private final SimuladoMapper mapper;

    SimuladoServiceImpl(SimuladoSessaoRepository sessaoRepository,
                        SimuladoSessaoQuestaoRepository sessaoQuestaoRepository,
                        RespostaSimuladoRepository respostaRepository,
                        ResultadoSimuladoRepository resultadoRepository,
                        QuestoesQueryService questoesQueryService,
                        SessaoAbandonoHelper sessaoAbandonoHelper,
                        SimuladoSessaoValidator validator,
                        SimuladoMapper mapper) {
        this.sessaoRepository = sessaoRepository;
        this.sessaoQuestaoRepository = sessaoQuestaoRepository;
        this.respostaRepository = respostaRepository;
        this.resultadoRepository = resultadoRepository;
        this.questoesQueryService = questoesQueryService;
        this.sessaoAbandonoHelper = sessaoAbandonoHelper;
        this.validator = validator;
        this.mapper = mapper;
    }

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

        SimuladoSessao sessao = SimuladoSessao.iniciar(usuarioId, questoes.get(0).provaId(), TEMPO_LIMITE_SEGUNDOS);
        sessao = sessaoRepository.save(sessao);

        int ordem = 1;
        for (QuestaoResumo q : questoes) {
            sessaoQuestaoRepository.save(new SimuladoSessaoQuestao(sessao.getId(), q.id(), ordem++));
        }

        return mapper.toSessaoDTO(sessao, questoes, Map.of());
    }

    /**
     * Limite diário de 1 simulado por usuário.
     * TODO: quando o módulo `assinatura` existir, mover a lógica de trial para lá
     * e permitir acesso ilimitado para assinantes.
     */
    private void verificarLimiteDiario(Long usuarioId) {
        LocalDateTime inicioDoDia = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        long simuladosHoje = sessaoRepository.countByUsuarioIdAndIniciadoEmAfter(usuarioId, inicioDoDia);
        if (simuladosHoje >= 1) {
            throw new LimiteExcedidoException(
                    "Limite diário de simulados atingido. Assine um plano para acesso ilimitado.");
        }
    }

    @Override
    @Transactional
    public void responderQuestao(Long usuarioId, Long sessaoId, int questaoId, ResponderQuestaoRequest request) {
        SimuladoSessao sessao = validator.buscarSessaoDoUsuario(sessaoId, usuarioId);
        validator.validarSessaoEmAndamento(sessao);

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
                        () -> respostaRepository.save(
                                new RespostaSimulado(sessaoId, questaoId, request.alternativaId()))
                );
    }

    @Override
    @Transactional
    public ResultadoDTO finalizarSimulado(Long usuarioId, Long sessaoId) {
        SimuladoSessao sessao = validator.buscarSessaoDoUsuario(sessaoId, usuarioId);

        if (sessao.getStatus() != StatusSessao.EM_ANDAMENTO) {
            throw new ConflitoDeEstadoException("Esta sessão já foi finalizada.");
        }

        List<Integer> questaoIds = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessaoId).stream()
                .map(SimuladoSessaoQuestao::getQuestaoId)
                .toList();

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
                sessaoId, total, acertos, erros, brancos, percentual, aprovado,
                (int) sessao.tempoGastoSegundos());
        resultadoRepository.save(resultado);

        return mapper.toResultadoDTO(resultado, questaoIds, respostas);
    }

    @Override
    @Transactional
    public void abandonarSimulado(Long usuarioId, Long sessaoId) {
        SimuladoSessao sessao = validator.buscarSessaoDoUsuario(sessaoId, usuarioId);
        validator.validarSessaoEmAndamento(sessao);

        sessao.abandonar();
        sessaoRepository.save(sessao);
    }
}