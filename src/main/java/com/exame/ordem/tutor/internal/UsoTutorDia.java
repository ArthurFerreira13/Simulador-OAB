package com.exame.ordem.tutor.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cota diária de uso do tutor (D08).
 *
 * Decisões aplicadas:
 *  - D06: usuario_id NÃO tem FK física para usuarios. Integridade
 *    referencial via evento UsuarioExcluido.
 *  - D08: cota diária. O número concreto (trial vs. pago) é decidido
 *    no serviço, a partir do PoliticaAcessoService. Esta entidade só conta.
 *
 * Duas métricas independentes:
 *  - contador: número de mensagens no dia.
 *  - tokens: soma dos tokens (entrada + saída) no dia.
 *
 * O freio real de custo é {@code tokens}; {@code contador} protege contra
 * abuso por frequência (muitas mensagens curtas).
 *
 * Reset é implícito: cada dia gera uma linha nova. A virada usa o Clock
 * central (America/Sao_Paulo, D30).
 *
 * Escrita por: TutorIaServiceImpl.
 * Removida por: listener de UsuarioExcluido (bloco posterior).
 */
@Entity
@Table(
        name = "uso_tutor_dia",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_uso_tutor_dia_usuario_data",
                columnNames = {"usuario_id", "data"}
        )
)
public class UsoTutorDia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "data", nullable = false, updatable = false)
    private LocalDate data;

    @Column(name = "contador", nullable = false)
    private int contador;

    @Column(name = "tokens", nullable = false)
    private long tokens;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected UsoTutorDia() {}

    public static UsoTutorDia iniciar(Long usuarioId, LocalDate data, LocalDateTime agora) {
        UsoTutorDia u = new UsoTutorDia();
        u.usuarioId = usuarioId;
        u.data = data;
        u.contador = 0;
        u.tokens = 0L;
        u.criadoEm = agora;
        u.atualizadoEm = agora;
        return u;
    }

    /**
     * Registra uma mensagem respondida e os tokens consumidos.
     *
     * Chamado depois que o LLM devolve a resposta completa (ou o parcial,
     * em caso de interrupção), para contabilizar o custo real.
     */
    public void registrar(int tokensConsumidos, LocalDateTime agora) {
        this.contador++;
        this.tokens += Math.max(0, tokensConsumidos);
        this.atualizadoEm = agora;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public LocalDate getData() { return data; }
    public int getContador() { return contador; }
    public long getTokens() { return tokens; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}