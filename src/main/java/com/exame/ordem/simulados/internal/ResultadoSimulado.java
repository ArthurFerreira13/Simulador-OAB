package com.exame.ordem.simulados.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "resultado_simulado")
public class ResultadoSimulado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sessao_id", nullable = false, unique = true)
    private Long sessaoId;

    @Column(name = "total_questoes", nullable = false)
    private Integer totalQuestoes;

    @Column(nullable = false)
    private Integer acertos;

    @Column(nullable = false)
    private Integer erros;

    @Column(nullable = false)
    private Integer brancos;

    @Column(name = "percentual_acerto", nullable = false)
    private BigDecimal percentualAcerto;

    @Column(nullable = false)
    private boolean aprovado;

    @Column(name = "tempo_gasto_segundos", nullable = false)
    private Integer tempoGastoSegundos;

    @Column(name = "calculado_em", nullable = false)
    private LocalDateTime calculadoEm = LocalDateTime.now(ZoneOffset.UTC);

    protected ResultadoSimulado() {}

    public ResultadoSimulado(Long sessaoId, int total, int acertos, int erros, int brancos,
                             BigDecimal percentual, boolean aprovado, int tempoGastoSegundos) {
        this.sessaoId = sessaoId;
        this.totalQuestoes = total;
        this.acertos = acertos;
        this.erros = erros;
        this.brancos = brancos;
        this.percentualAcerto = percentual;
        this.aprovado = aprovado;
        this.tempoGastoSegundos = tempoGastoSegundos;
    }

    public Long getSessaoId() { return sessaoId; }
    public Integer getTotalQuestoes() { return totalQuestoes; }
    public Integer getAcertos() { return acertos; }
    public Integer getErros() { return erros; }
    public Integer getBrancos() { return brancos; }
    public BigDecimal getPercentualAcerto() { return percentualAcerto; }
    public boolean isAprovado() { return aprovado; }
    public Integer getTempoGastoSegundos() { return tempoGastoSegundos; }
}