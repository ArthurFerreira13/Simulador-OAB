package com.exame.ordem.simulados;

import com.exame.ordem.simulados.dto.*;

public interface SimuladoService {
    SessaoDTO iniciarSimulado(IniciarSimuladoRequest request);
    void responderQuestao(Long sessaoId, int questaoId, ResponderQuestaoRequest request);
    ResultadoDTO finalizarSimulado(Long sessaoId);
    ResultadoDTO consultarResultado(Long sessaoId);
    SessaoDTO consultarSessaoAtiva(String email, String senha);
}