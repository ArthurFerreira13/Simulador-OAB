package com.exame.ordem.tutor.service;

import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import com.exame.ordem.tutor.internal.UsoTutorDia;
import com.exame.ordem.tutor.internal.UsoTutorDiaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
        if (limiteMensagens <= 0 || limiteTokens <= 0) {
            throw new RegraDeNegocioException("Acesso ao tutor não disponível.");
        }

        LocalDateTime agora = LocalDateTime.now(clock);
        int reservou = usoRepository.reservarAtomico(
                usuarioId, dia, limiteMensagens, limiteTokens, agora);

        if (reservou == 0) {
            String motivo = usoRepository.findByUsuarioIdAndData(usuarioId, dia)
                    .map(uso -> uso.getContador() >= limiteMensagens
                            ? "Limite diário de mensagens do tutor atingido (" + limiteMensagens + ")."
                            : "Limite diário de tokens do tutor atingido (" + limiteTokens + ").")
                    .orElse("Limite diário de uso do tutor atingido.");
            throw new RegraDeNegocioException(motivo);
        }
    }

    @Transactional(readOnly = true)
    public UsoTutorDia consultar(Long usuarioId, LocalDate dia) {
        return usoRepository.findByUsuarioIdAndData(usuarioId, dia).orElse(null);
    }

    @Transactional(readOnly = true)
    public int mensagensUsadas(Long usuarioId, LocalDate dia) {
        return usoRepository.findByUsuarioIdAndData(usuarioId, dia)
                .map(UsoTutorDia::getContador)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public long tokensUsados(Long usuarioId, LocalDate dia) {
        return usoRepository.findByUsuarioIdAndData(usuarioId, dia)
                .map(UsoTutorDia::getTokens)
                .orElse(0L);
    }

    @Transactional
    public void somarTokens(Long usuarioId, LocalDate dia, int tokens, LocalDateTime atualizadoEm) {
        if (tokens > 0) {
            usoRepository.somarTokens(usuarioId, dia, tokens, atualizadoEm);
        }
    }

    @Transactional
    public void estornar(Long usuarioId, LocalDate dia, LocalDateTime atualizadoEm) {
        usoRepository.estornar(usuarioId, dia, atualizadoEm);
    }
}