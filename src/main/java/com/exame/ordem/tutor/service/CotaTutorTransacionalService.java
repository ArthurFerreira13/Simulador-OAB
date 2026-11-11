package com.exame.ordem.tutor.service;

import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import com.exame.ordem.tutor.internal.UsoTutorDia;
import com.exame.ordem.tutor.internal.UsoTutorDiaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Operações de persistência da cota do tutor: cada chamada tem sua própria transação curta. */
@Service
public class CotaTutorTransacionalService {

    private final UsoTutorDiaRepository usoRepository;
    private final Clock clock;

    public CotaTutorTransacionalService(UsoTutorDiaRepository usoRepository, Clock clock) {
        this.usoRepository = usoRepository;
        this.clock = clock;
    }

    @Transactional
    public void reservar(Long usuarioId, LocalDate dia, int limiteMensagens, long limiteTokens) {
        LocalDateTime agora = LocalDateTime.now(clock);
        usoRepository.garantirLinha(usuarioId, dia, agora);

        int reservou = usoRepository.reservar(usuarioId, dia, limiteMensagens, limiteTokens, agora);
        if (reservou == 0) {
            UsoTutorDia uso = usoRepository.findByUsuarioIdAndData(usuarioId, dia).orElseThrow();
            if (uso.getContador() >= limiteMensagens) {
                throw new RegraDeNegocioException(
                        "Limite diário de mensagens do tutor atingido (" + limiteMensagens + ").");
            }
            throw new RegraDeNegocioException(
                    "Limite diário de tokens do tutor atingido (" + limiteTokens + ").");
        }
    }

    @Transactional(readOnly = true)
    public UsoTutorDia consultar(Long usuarioId, LocalDate dia) {
        return usoRepository.findByUsuarioIdAndData(usuarioId, dia).orElse(null);
    }

    @Transactional
    public void somarTokens(Long usuarioId, LocalDate dia, int tokens, LocalDateTime atualizadoEm) {
        usoRepository.somarTokens(usuarioId, dia, tokens, atualizadoEm);
    }

    @Transactional
    public void estornar(Long usuarioId, LocalDate dia, LocalDateTime atualizadoEm) {
        usoRepository.estornar(usuarioId, dia, atualizadoEm);
    }
}