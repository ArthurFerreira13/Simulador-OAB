package com.exame.ordem.usuarios.service;

import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import com.exame.ordem.usuarios.EstadoAcesso;
import com.exame.ordem.usuarios.PoliticaAcessoService;
import com.exame.ordem.usuarios.RecursoAcesso;
import com.exame.ordem.usuarios.dto.AcessoResumo;
import com.exame.ordem.usuarios.internal.Usuario;
import com.exame.ordem.usuarios.internal.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
class PoliticaAcessoServiceImpl implements PoliticaAcessoService {

    private final UsuarioRepository usuarioRepository;
    private final Clock clock;
    private final int limiteDiarioGratuito;

    PoliticaAcessoServiceImpl(UsuarioRepository usuarioRepository,
                              Clock clock,
                              @Value("${app.simulado.limite-diario:1}") int limiteDiarioGratuito) {
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
        this.limiteDiarioGratuito = limiteDiarioGratuito;
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoAcesso estadoDe(Long usuarioId) {
        return derivarEstado(carregar(usuarioId));
    }

    @Override
    @Transactional(readOnly = true)
    public AcessoResumo consultarAcesso(Long usuarioId) {
        Usuario u = carregar(usuarioId);
        EstadoAcesso estado = derivarEstado(u);
        return new AcessoResumo(
                estado,
                diasRestantesTrial(estado, u),
                limiteSimuladoPorEstado(estado),
                recursosPorEstado(estado));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean permite(Long usuarioId, RecursoAcesso recurso) {
        return recursosPorEstado(estadoDe(usuarioId)).contains(recurso);
    }

    @Override
    @Transactional(readOnly = true)
    public int limiteDiarioSimulado(Long usuarioId) {
        return limiteSimuladoPorEstado(estadoDe(usuarioId));
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private Usuario carregar(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private EstadoAcesso derivarEstado(Usuario u) {
        Instant agora = Instant.now(clock);
        if (u.getFimTrial() != null && agora.isBefore(u.getFimTrial())) {
            return EstadoAcesso.TRIAL;
        }
        // PAGO virá no MVP 7 (assinatura)
        return EstadoAcesso.GRATUITO;
    }

    private Long diasRestantesTrial(EstadoAcesso estado, Usuario u) {
        if (estado != EstadoAcesso.TRIAL || u.getFimTrial() == null) {
            return null;
        }
        long segundos = Duration.between(Instant.now(clock), u.getFimTrial()).getSeconds();
        // Arredonda para cima: 6 dias e 2h → 7
        return Math.max(0L, (segundos + 86_399) / 86_400);
    }

    private int limiteSimuladoPorEstado(EstadoAcesso estado) {
        return estado == EstadoAcesso.GRATUITO ? limiteDiarioGratuito : 0;
    }

    private Set<RecursoAcesso> recursosPorEstado(EstadoAcesso estado) {
        return switch (estado) {
            case TRIAL, PAGO -> EnumSet.allOf(RecursoAcesso.class);
            case GRATUITO -> EnumSet.noneOf(RecursoAcesso.class);
        };
    }
}