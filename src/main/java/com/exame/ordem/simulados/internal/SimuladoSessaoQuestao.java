package com.exame.ordem.simulados.internal;

import jakarta.persistence.*;

@Entity
@Table(name = "simulado_sessao_questao")
public class SimuladoSessaoQuestao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sessao_id", nullable = false)
    private Long sessaoId;

    @Column(name = "questao_id", nullable = false)
    private Integer questaoId;

    @Column(nullable = false)
    private Integer ordem;

    @Column(name = "marcada_revisao", nullable = false)
    private boolean marcadaRevisao = false;

    protected SimuladoSessaoQuestao() {}

    public SimuladoSessaoQuestao(Long sessaoId, Integer questaoId, Integer ordem) {
        this.sessaoId = sessaoId;
        this.questaoId = questaoId;
        this.ordem = ordem;
    }

    public Long getId() { return id; }
    public Long getSessaoId() { return sessaoId; }
    public Integer getQuestaoId() { return questaoId; }
    public Integer getOrdem() { return ordem; }
    public boolean isMarcadaRevisao() { return marcadaRevisao; }
}