package com.exame.ordem.legislacao.service;

import com.exame.ordem.legislacao.dto.FiltroSenadoLegislacao;
import com.exame.ordem.legislacao.dto.ItemIngestaoLegislacao;

import java.util.List;

public interface SenadoLegislacaoGateway {
    List<ItemIngestaoLegislacao> buscarNormas(FiltroSenadoLegislacao filtro);
}