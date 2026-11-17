package com.exame.ordem.tutor.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repositório do uso diário do tutor.
 *
 * <p><b>Performance (row lock):</b> {@link #reservarAtomico} combina INSERT + UPDATE
 * em um único {@code INSERT ... ON CONFLICT DO UPDATE}. Isso é atômico no PostgreSQL
 * (docs: "ON CONFLICT DO UPDATE guarantees an atomic INSERT or UPDATE outcome") e
 * elimina a janela em que duas requisições paralelas criavam/atualizavam a linha em
 * duas operações distintas — que é o que gerava o {@code Lock:transactionid} e a fila
 * de 37s observados no teste de 100 requests do mesmo usuário.
 *
 * <p>O {@code WHERE} dentro do {@code DO UPDATE} é o guardião dos limites de
 * mensagens e tokens já contabilizados. Se algum limite falhar,
 * nenhuma linha é atualizada e o rowcount volta 0. A aplicação então faz um
 * SELECT (só no caminho de falha) para descobrir qual limite estourou.
 *
 * <p><b>Nota sobre @Transactional nos métodos:</b> é defesa em profundidade.
 * Normalmente o caller ({@link com.exame.ordem.tutor.service.CotaTutorTransacionalService})
 * já abre transação, e a propagação REQUIRED apenas junta. Mas se algum código chamar
 * direto sem service, ainda funciona.
 */
public interface UsoTutorDiaRepository extends JpaRepository<UsoTutorDia, Long> {

    Optional<UsoTutorDia> findByUsuarioIdAndData(Long usuarioId, LocalDate data);

    /**
     * Conta uma mensagem se os limites ainda permitirem, criando a linha do dia
     * se necessário.
     *
     * @return 1 se contabilizou; 0 se os limites de mensagens ou tokens forem atingidos
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO uso_tutor_dia (usuario_id, data, contador, tokens, criado_em, atualizado_em)
            VALUES (:usuarioId, :data, 1, 0, :agora, :agora)
            ON CONFLICT (usuario_id, data) DO UPDATE
               SET contador = uso_tutor_dia.contador + 1,
                   atualizado_em = :agora
             WHERE uso_tutor_dia.contador < :limiteMensagens
               AND uso_tutor_dia.tokens < :limiteTokens
            """, nativeQuery = true)
    int reservarAtomico(@Param("usuarioId") Long usuarioId,
                        @Param("data") LocalDate data,
                        @Param("limiteMensagens") int limiteMensagens,
                        @Param("limiteTokens") long limiteTokens,
                        @Param("agora") LocalDateTime agora);

    /** Devolve a mensagem quando a geração falha antes do primeiro token. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UsoTutorDia u
               set u.contador = u.contador - 1, u.atualizadoEm = :agora
             where u.usuarioId = :usuarioId and u.data = :data
               and u.contador > 0
            """)
    int estornar(@Param("usuarioId") Long usuarioId,
                 @Param("data") LocalDate data,
                 @Param("agora") LocalDateTime agora);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UsoTutorDia u
               set u.tokens = u.tokens + :tokens, u.atualizadoEm = :agora
             where u.usuarioId = :usuarioId and u.data = :data
            """)
    int somarTokens(@Param("usuarioId") Long usuarioId,
                    @Param("data") LocalDate data,
                    @Param("tokens") long tokens,
                    @Param("agora") LocalDateTime agora);
}