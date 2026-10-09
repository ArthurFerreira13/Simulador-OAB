package com.exame.ordem.simulados;

public interface SimuladoRevisaoService {

    void marcarRevisao(Long usuarioId, Long sessaoId, int questaoId);

    void desmarcarRevisao(Long usuarioId, Long sessaoId, int questaoId);
}