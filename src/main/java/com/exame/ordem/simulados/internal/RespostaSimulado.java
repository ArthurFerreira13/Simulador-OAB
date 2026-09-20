package com.exame.ordem.simulados.internal;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Resposta de um usuário a uma questão de uma sessão de simulado.
 *
 * Apenas alternativaId e respondidoEm podem mudar (troca de resposta).
 * sessaoId e questaoId são imutáveis após a criação.
 */
@Entity
@Table(name = "resposta_simulado")
public class RespostaSimulado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sessao_id", nullable = false, updatable = false)
    private Long sessaoId;

    @Column(name = "questao_id", nullable = false, updatable = false)
    private Integer questaoId;

    @Column(name = "alternativa_id")
    private Long alternativaId;

    @Column(name = "respondido_em", nullable = false)
    private LocalDateTime respondidoEm = LocalDateTime.now(ZoneOffset.UTC);

    protected RespostaSimulado() {}

    public RespostaSimulado(Long sessaoId, Integer questaoId, Long alternativaId) {
        this.sessaoId = sessaoId;
        this.questaoId = questaoId;
        this.alternativaId = alternativaId;
    }

    public void atualizarResposta(Long novaAlternativaId) {
        this.alternativaId = novaAlternativaId;
        this.respondidoEm = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getSessaoId() { return sessaoId; }
    public Integer getQuestaoId() { return questaoId; }
    public Long getAlternativaId() { return alternativaId; }
    public LocalDateTime getRespondidoEm() { return respondidoEm; }
}