package com.exame.ordem.questoes;

import com.exame.ordem.questoes.dto.DisciplinaResumo;
import com.exame.ordem.questoes.dto.ProvaResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;

import java.util.List;

/**
 * Porta pública do módulo questoes para consulta e listagem de conteúdo.
 *
 * Somente leitura — verificação de resposta e gabarito moram em GabaritoService.
 */
public interface QuestoesQueryService {

    List<QuestaoResumo> buscarQuestoesPorProva(int numeroProva);

    List<QuestaoResumo> buscarPorIds(List<Integer> questaoIds);

    List<QuestaoResumo> buscarQuestoesPorDisciplina(Long disciplinaId);

    List<QuestaoResumo> buscarPorDisciplinaEAno(Long disciplinaId, Integer ano);

    QuestaoResumo buscarQuestaoPorId(int questaoId);

    List<ProvaResumo> listarProvas();

    List<DisciplinaResumo> listarDisciplinas();
}