package com.exame.ordem.simulados.service;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.simulados.SimuladoConsultaService;
import com.exame.ordem.simulados.dto.ResultadoDTO;
import com.exame.ordem.simulados.dto.SessaoDTO;
import com.exame.ordem.simulados.internal.*;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
class SimuladoConsultaServiceImpl implements SimuladoConsultaService {

    private final SimuladoSessaoRepository sessaoRepository;
    private final SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;
    private final RespostaSimuladoRepository respostaRepository;
    private final ResultadoSimuladoRepository resultadoRepository;
    private final QuestoesQueryService questoesQueryService;
    private final SessaoAbandonoHelper sessaoAbandonoHelper;
    private final SimuladoMapper mapper;

    SimuladoConsultaServiceImpl(SimuladoSessaoRepository sessaoRepository,
                                SimuladoSessaoQuestaoRepository sessaoQuestaoRepository,
                                RespostaSimuladoRepository respostaRepository,
                                ResultadoSimuladoRepository resultadoRepository,
                                QuestoesQueryService questoesQueryService,
                                SessaoAbandonoHelper sessaoAbandonoHelper,
                                SimuladoMapper mapper) {
        this.sessaoRepository = sessaoRepository;
        this.sessaoQuestaoRepository = sessaoQuestaoRepository;
        this.respostaRepository = respostaRepository;
        this.resultadoRepository = resultadoRepository;
        this.questoesQueryService = questoesQueryService;
        this.sessaoAbandonoHelper = sessaoAbandonoHelper;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public SessaoDTO consultarSessaoAtiva(Long usuarioId) {
        SimuladoSessao sessao = sessaoRepository
                .findByUsuarioIdAndStatus(usuarioId, StatusSessao.EM_ANDAMENTO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nenhum simulado em andamento."));

        if (sessao.tempoExpirado()) {
            sessaoAbandonoHelper.abandonarSeExpirada(sessao.getId());
            throw new RecursoNaoEncontradoException("Nenhum simulado em andamento.");
        }

        List<Integer> questaoIds = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessao.getId()).stream()
                .map(SimuladoSessaoQuestao::getQuestaoId)
                .toList();
        List<QuestaoResumo> questoes = questoesQueryService.buscarPorIds(questaoIds);

        Map<Integer, Long> respostas = respostaRepository.findBySessaoId(sessao.getId()).stream()
                .collect(Collectors.toMap(RespostaSimulado::getQuestaoId, RespostaSimulado::getAlternativaId));

        return mapper.toSessaoDTO(sessao, questoes, respostas);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoDTO consultarResultado(Long usuarioId, Long sessaoId) {
        // Valida que a sessão pertence ao usuário
        SimuladoSessao sessao = sessaoRepository.findByIdAndUsuarioId(sessaoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão não encontrada."));

        ResultadoSimulado resultado = resultadoRepository.findBySessaoId(sessao.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Resultado ainda não calculado."));

        List<Integer> questaoIds = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessaoId).stream()
                .map(SimuladoSessaoQuestao::getQuestaoId)
                .toList();

        Map<Integer, Long> respostas = respostaRepository.findBySessaoId(sessaoId).stream()
                .collect(Collectors.toMap(RespostaSimulado::getQuestaoId, RespostaSimulado::getAlternativaId));

        return mapper.toResultadoDTO(resultado, questaoIds, respostas);
    }
}