package com.exame.ordem.simulados.internal;

import jakarta.persistence.*;

/**
 * Vínculo entre uma sessão e uma questão da prova.
 *
 * Apenas o campo marcadaRevisao pode ser alterado durante a sessão.
 * sessaoId, questaoId e ordem são imutáveis após a criação.
 */
@Entity
@Table(name = "simulado_sessao_questao")
public class SimuladoSessaoQuestao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sessao_id", nullable = false, updatable = false)
    private Long sessaoId;

    @Column(name = "questao_id", nullable = false, updatable = false)
    private Integer questaoId;

    @Column(nullable = false, updatable = false)
    private Integer ordem;

    @Column(name = "marcada_revisao", nullable = false)
    private boolean marcadaRevisao = false;

    protected SimuladoSessaoQuestao() {}

    public SimuladoSessaoQuestao(Long sessaoId, Integer questaoId, Integer ordem) {
        this.sessaoId = sessaoId;
        this.questaoId = questaoId;
        this.ordem = ordem;
    }

    public void marcarRevisao() {
        this.marcadaRevisao = true;
    }

    public void desmarcarRevisao() {
        this.marcadaRevisao = false;
    }

    public Long getId() { return id; }
    public Long getSessaoId() { return sessaoId; }
    public Integer getQuestaoId() { return questaoId; }
    public Integer getOrdem() { return ordem; }
    public boolean isMarcadaRevisao() { return marcadaRevisao; }
}