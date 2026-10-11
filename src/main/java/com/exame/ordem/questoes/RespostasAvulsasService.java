package com.exame.ordem.questoes;

import com.exame.ordem.questoes.dto.RespostaDetalhadaDTO;
import com.exame.ordem.questoes.dto.StatusQuestaoUsuario;

import java.util.List;
import java.util.Map;

public interface RespostasAvulsasService {

    /**
     * Registra (ou atualiza) a resposta do usuário a uma questão avulsa.
     * A alternativa é identificada por letra (A-D), não por ID.
     */
    RespostaDetalhadaDTO responder(int questaoId, String alternativa, Long usuarioId);

    StatusQuestaoUsuario consultarStatus(int questaoId, Long usuarioId);

    Map<Integer, StatusQuestaoUsuario> consultarStatusEmLote(List<Integer> questaoIds, Long usuarioId);

    void resetarEstatisticas(Long usuarioId);
}