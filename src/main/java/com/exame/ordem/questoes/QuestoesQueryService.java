package com.exame.ordem.questoes;

import com.exame.ordem.questoes.dto.GabaritoResumo;
import com.exame.ordem.questoes.dto.QuestaoComGabaritoDTO;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.questoes.dto.RespostaDetalhadaDTO;

import java.util.List;

public interface QuestoesQueryService {
    List<QuestaoResumo> buscarQuestoesPorProva(int numeroProva);
    RespostaDetalhadaDTO verificarResposta(int questaoId, Long alternativaId);
    List<GabaritoResumo> buscarGabarito(List<Integer> questaoIds);
    List<QuestaoResumo> buscarPorIds(List<Integer> questaoIds);
    List<QuestaoResumo> buscarQuestoesPorDisciplina(Long disciplinaId);
    List<QuestaoComGabaritoDTO> buscarComGabarito(List<Integer> questaoIds);
}