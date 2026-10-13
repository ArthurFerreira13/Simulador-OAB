package com.exame.ordem.questoes.service;

import com.exame.ordem.questoes.GabaritoService;
import com.exame.ordem.questoes.RespostasAvulsasService;
import com.exame.ordem.questoes.dto.RespostaDetalhadaDTO;
import com.exame.ordem.questoes.dto.StatusQuestaoUsuario;
import com.exame.ordem.questoes.internal.RespostaAvulsa;
import com.exame.ordem.questoes.internal.RespostaAvulsaRepository;
import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import com.exame.ordem.usuarios.PoliticaAcessoService;
import com.exame.ordem.usuarios.RecursoAcesso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
class RespostasAvulsasServiceImpl implements RespostasAvulsasService {

    private final RespostaAvulsaRepository respostaRepository;
    private final GabaritoService gabaritoService;
    private final PoliticaAcessoService politicaAcesso;

    RespostasAvulsasServiceImpl(RespostaAvulsaRepository respostaRepository,
                                GabaritoService gabaritoService,
                                PoliticaAcessoService politicaAcesso) {
        this.respostaRepository = respostaRepository;
        this.gabaritoService = gabaritoService;
        this.politicaAcesso = politicaAcesso;
    }

    @Override
    @Transactional
    public RespostaDetalhadaDTO responder(int questaoId, String alternativa, Long usuarioId) {
        if (!politicaAcesso.permite(usuarioId, RecursoAcesso.QUESTAO_AVULSA)) {
            throw new RegraDeNegocioException(
                    "Questões avulsas estão disponíveis apenas no trial ou em um plano pago.");
        }

        RespostaDetalhadaDTO detalhada = gabaritoService.verificarResposta(questaoId, alternativa);

        respostaRepository.findByUsuarioIdAndQuestaoId(usuarioId, questaoId)
                .ifPresentOrElse(
                        r -> r.atualizar(detalhada.alternativaEscolhidaId(), detalhada.correta()),
                        () -> respostaRepository.save(
                                RespostaAvulsa.criar(usuarioId, questaoId,
                                        detalhada.alternativaEscolhidaId(), detalhada.correta()))
                );

        return detalhada;
    }

    @Override
    @Transactional(readOnly = true)
    public StatusQuestaoUsuario consultarStatus(int questaoId, Long usuarioId) {
        return respostaRepository.findByUsuarioIdAndQuestaoId(usuarioId, questaoId)
                .map(r -> new StatusQuestaoUsuario(
                        true, r.isCorreta(), r.getAlternativaId(), r.getRespondidoEm()))
                .orElseGet(StatusQuestaoUsuario::naoRespondida);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, StatusQuestaoUsuario> consultarStatusEmLote(List<Integer> questaoIds, Long usuarioId) {
        if (questaoIds.isEmpty()) {
            return Map.of();
        }
        return respostaRepository.findByUsuarioIdAndQuestaoIdIn(usuarioId, questaoIds).stream()
                .collect(Collectors.toMap(
                        RespostaAvulsa::getQuestaoId,
                        r -> new StatusQuestaoUsuario(
                                true, r.isCorreta(), r.getAlternativaId(), r.getRespondidoEm())
                ));
    }

    @Override
    @Transactional
    public void resetarEstatisticas(Long usuarioId) {
        respostaRepository.deleteByUsuarioId(usuarioId);
    }
}