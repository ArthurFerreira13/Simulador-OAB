package com.exame.ordem.simulados;

import com.exame.ordem.simulados.dto.ResultadoDTO;
import com.exame.ordem.simulados.dto.SessaoDTO;

public interface SimuladoConsultaService {

    SessaoDTO consultarSessaoAtiva(Long usuarioId);

    ResultadoDTO consultarResultado(Long usuarioId, Long sessaoId);
}