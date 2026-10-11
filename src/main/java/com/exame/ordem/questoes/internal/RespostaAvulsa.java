package com.exame.ordem.questoes.internal;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Resposta de um usuário a uma questão em modo avulso (fora de simulado).
 *
 * Regras:
 *  - Unicidade por (usuario_id, questao_id) — última resposta prevalece.
 *  - usuario_id e questao_id são imutáveis após a criação.
 *  - alternativa_id, correta e respondido_em mudam quando o usuário responde de novo.
 *
 * Não confundir com RespostaSimulado (módulo simulados).
 */
@Entity
@Table(name = "resposta_avulsa")
public class RespostaAvulsa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "questao_id", nullable = false, updatable = false)
    private Integer questaoId;

    @Column(name = "alternativa_id", nullable = false)
    private Long alternativaId;

    @Column(nullable = false)
    private boolean correta;

    @Column(name = "respondido_em", nullable = false)
    private LocalDateTime respondidoEm = LocalDateTime.now(ZoneOffset.UTC);

    protected RespostaAvulsa() {}

    public static RespostaAvulsa criar(Long usuarioId, Integer questaoId,
                                       Long alternativaId, boolean correta) {
        RespostaAvulsa r = new RespostaAvulsa();
        r.usuarioId = usuarioId;
        r.questaoId = questaoId;
        r.alternativaId = alternativaId;
        r.correta = correta;
        return r;
    }

    public void atualizar(Long novaAlternativaId, boolean novaCorreta) {
        this.alternativaId = novaAlternativaId;
        this.correta = novaCorreta;
        this.respondidoEm = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public Integer getQuestaoId() { return questaoId; }
    public Long getAlternativaId() { return alternativaId; }
    public boolean isCorreta() { return correta; }
    public LocalDateTime getRespondidoEm() { return respondidoEm; }
}