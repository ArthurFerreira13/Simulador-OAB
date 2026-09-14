package com.exame.ordem.simulados.internal;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "simulado_sessao")
public class SimuladoSessao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "prova_id", nullable = false)
    private Integer provaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSessao status;

    @Column(name = "tempo_limite_segundos", nullable = false)
    private Integer tempoLimiteSegundos;

    @Column(name = "iniciado_em", nullable = false)
    private LocalDateTime iniciadoEm = LocalDateTime.now(ZoneOffset.UTC);

    @Column(name = "finalizado_em")
    private LocalDateTime finalizadoEm;

    protected SimuladoSessao() {}

    public static SimuladoSessao iniciar(Long usuarioId, Integer provaId, int tempoLimiteSegundos) {
        SimuladoSessao sessao = new SimuladoSessao();
        sessao.usuarioId = usuarioId;
        sessao.provaId = provaId;
        sessao.tempoLimiteSegundos = tempoLimiteSegundos;
        sessao.status = StatusSessao.EM_ANDAMENTO;
        return sessao;
    }

    public void finalizar() {
        this.status = StatusSessao.CONCLUIDO;
        this.finalizadoEm = LocalDateTime.now(ZoneOffset.UTC);
    }

    public long tempoGastoSegundos() {
        LocalDateTime fim = finalizadoEm != null ? finalizadoEm : LocalDateTime.now(ZoneOffset.UTC);
        return Duration.between(iniciadoEm, fim).getSeconds();
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public Integer getProvaId() { return provaId; }
    public StatusSessao getStatus() { return status; }
    public Integer getTempoLimiteSegundos() { return tempoLimiteSegundos; }
    public LocalDateTime getIniciadoEm() { return iniciadoEm; }
    public LocalDateTime getFinalizadoEm() { return finalizadoEm; }
}