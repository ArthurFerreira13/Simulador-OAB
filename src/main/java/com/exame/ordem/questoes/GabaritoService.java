package com.exame.ordem.questoes;

import com.exame.ordem.questoes.dto.GabaritoResumo;
import com.exame.ordem.questoes.dto.QuestaoRevisadaDTO;
import com.exame.ordem.questoes.dto.RespostaDetalhadaDTO;

import java.util.List;
import java.util.Map;

public interface GabaritoService {

    /**
     * Verifica a resposta do usuário. A alternativa é identificada por letra (A-D),
     * não por ID — a letra é o identificador de domínio.
     */
    RespostaDetalhadaDTO verificarResposta(int questaoId, String letra);

    boolean existeAlternativaNaQuestao(int questaoId, Long alternativaId);

    List<GabaritoResumo> buscarGabarito(List<Integer> questaoIds);

    List<QuestaoRevisadaDTO> montarRevisao(List<Integer> questaoIds, Map<Integer, Long> respostas);
}