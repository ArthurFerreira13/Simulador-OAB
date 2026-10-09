package com.exame.ordem.simulados.internal;

import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.questoes.dto.QuestaoRevisadaDTO;
import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.simulados.dto.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Monta DTOs de simulado a partir de entidades internas + dados do módulo questoes.
 */
@Component
public class SimuladoMapper {

    private final SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;
    private final QuestoesQueryService questoesQueryService;

    SimuladoMapper(SimuladoSessaoQuestaoRepository sessaoQuestaoRepository,
                   QuestoesQueryService questoesQueryService) {
        this.sessaoQuestaoRepository = sessaoQuestaoRepository;
        this.questoesQueryService = questoesQueryService;
    }

    public SessaoDTO toSessaoDTO(SimuladoSessao sessao,
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

    public ResultadoDTO toResultadoDTO(ResultadoSimulado resultado,
                                       List<Integer> questaoIds,
                                       Map<Integer, Long> respostas) {
        List<QuestaoRevisadaDTO> revisao = questoesQueryService.buscarComGabarito(questaoIds).stream()
                .map(q -> {
                    Long escolhida = respostas.get(q.questaoId());
                    boolean correta = q.alternativas().stream()
                            .anyMatch(a -> a.ehCorreta() && a.id().equals(escolhida));
                    return new QuestaoRevisadaDTO(q, escolhida, correta);
                })
                .toList();

        return new ResultadoDTO(
                resultado.getTotalQuestoes(), resultado.getAcertos(), resultado.getErros(),
                resultado.getBrancos(), resultado.getPercentualAcerto(), resultado.isAprovado(),
                resultado.getTempoGastoSegundos(), revisao);
    }
}