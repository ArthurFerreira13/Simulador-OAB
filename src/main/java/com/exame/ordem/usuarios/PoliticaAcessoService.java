package com.exame.ordem.usuarios;

import com.exame.ordem.usuarios.dto.AcessoResumo;

/**
 * Porta pública de acesso (ADR-011). Ponto único de decisão sobre o que
 * cada usuário pode usar.
 *
 * <p>Consultada por {@code simulados}, {@code questoes}, {@code tutor-ia}
 * e demais módulos. Nenhum módulo reimplementa a regra (RNF21).
 */
public interface PoliticaAcessoService {

    EstadoAcesso estadoDe(Long usuarioId);

    AcessoResumo consultarAcesso(Long usuarioId);

    boolean permite(Long usuarioId, RecursoAcesso recurso);

    int limiteDiarioSimulado(Long usuarioId);
}