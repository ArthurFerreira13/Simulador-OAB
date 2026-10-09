package com.exame.ordem.simulados.service;

import com.exame.ordem.simulados.SimuladoRevisaoService;
import com.exame.ordem.simulados.internal.SimuladoSessao;
import com.exame.ordem.simulados.internal.SimuladoSessaoQuestao;
import com.exame.ordem.simulados.internal.SimuladoSessaoQuestaoRepository;
import com.exame.ordem.simulados.internal.SimuladoSessaoValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SimuladoRevisaoServiceImpl implements SimuladoRevisaoService {

    private final SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;
    private final SimuladoSessaoValidator validator;

    SimuladoRevisaoServiceImpl(SimuladoSessaoQuestaoRepository sessaoQuestaoRepository,
                               SimuladoSessaoValidator validator) {
        this.sessaoQuestaoRepository = sessaoQuestaoRepository;
        this.validator = validator;
    }

    @Override
    @Transactional
    public void marcarRevisao(Long usuarioId, Long sessaoId, int questaoId) {
        SimuladoSessao sessao = validator.buscarSessaoDoUsuario(sessaoId, usuarioId);
        validator.validarSessaoEmAndamento(sessao);

        SimuladoSessaoQuestao vinculo = validator.buscarVinculo(sessaoId, questaoId);
        vinculo.marcarRevisao();
        sessaoQuestaoRepository.save(vinculo);
    }

    @Override
    @Transactional
    public void desmarcarRevisao(Long usuarioId, Long sessaoId, int questaoId) {
        SimuladoSessao sessao = validator.buscarSessaoDoUsuario(sessaoId, usuarioId);
        validator.validarSessaoEmAndamento(sessao);

        SimuladoSessaoQuestao vinculo = validator.buscarVinculo(sessaoId, questaoId);
        vinculo.desmarcarRevisao();
        sessaoQuestaoRepository.save(vinculo);
    }
}