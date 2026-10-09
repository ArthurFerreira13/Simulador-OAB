package com.exame.ordem.simulados;

import com.exame.ordem.simulados.dto.*;

public interface SimuladoService {

    SessaoDTO iniciarSimulado(Long usuarioId, IniciarSimuladoRequest request);

    void responderQuestao(Long usuarioId, Long sessaoId, int questaoId, ResponderQuestaoRequest request);

    ResultadoDTO finalizarSimulado(Long usuarioId, Long sessaoId);

    void abandonarSimulado(Long usuarioId, Long sessaoId);
}