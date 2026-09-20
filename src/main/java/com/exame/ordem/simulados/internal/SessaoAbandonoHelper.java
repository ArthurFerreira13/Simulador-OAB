package com.exame.ordem.simulados.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isola o abandono de sessão expirada em uma transação própria (REQUIRES_NEW),
 * garantindo que o UPDATE seja commitado mesmo que o método chamador
 * lance uma exceção logo em seguida (e sofra rollback).
 */
@Component
public class SessaoAbandonoHelper {

    private final SimuladoSessaoRepository sessaoRepository;

    SessaoAbandonoHelper(SimuladoSessaoRepository sessaoRepository) {
        this.sessaoRepository = sessaoRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abandonarSeExpirada(Long sessaoId) {
        sessaoRepository.findById(sessaoId).ifPresent(sessao -> {
            if (sessao.getStatus() == StatusSessao.EM_ANDAMENTO && sessao.tempoExpirado()) {
                sessao.abandonar();
                sessaoRepository.save(sessao);
            }
        });
    }
}