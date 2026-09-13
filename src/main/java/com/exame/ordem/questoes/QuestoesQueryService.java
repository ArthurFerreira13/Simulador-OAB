package com.exame.ordem.questoes;

import com.exame.ordem.questoes.dto.QuestaoResumo;

import java.util.List;

public interface QuestoesQueryService {
    List<QuestaoResumo> buscarQuestoesPorProva(int numeroProva);
    boolean verificarResposta(int questaoId, Long alternativaId);

}
