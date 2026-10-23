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
 * <p>A cota é reservada de forma atômica no banco ({@link #reservar}) antes de chamar o LLM.
 * Antes, o contador só subia quando a resposta terminava, então várias requisições paralelas
 * passavam do limite. Os métodos de escrita têm {@code @Transactional} próprio porque o
 * fechamento (tokens/estorno) roda na thread do streaming, fora da transação do serviço.
 */
public interface UsoTutorDiaRepository extends JpaRepository<UsoTutorDia, Long> {

    Optional<UsoTutorDia> findByUsuarioIdAndData(Long usuarioId, LocalDate data);

    /** Cria a linha do dia se ainda não existir (seguro sob concorrência: ON CONFLICT DO NOTHING). */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO uso_tutor_dia (usuario_id, data, contador, tokens, criado_em, atualizado_em)
            VALUES (:usuarioId, :data, 0, 0, :agora, :agora)
            ON CONFLICT (usuario_id, data) DO NOTHING
            """, nativeQuery = true)
    void garantirLinha(@Param("usuarioId") Long usuarioId,
                       @Param("data") LocalDate data,
                       @Param("agora") LocalDateTime agora);

    /**
     * Reserva uma mensagem se, e só se, os dois limites ainda não foram atingidos.
     *
     * @return 1 se reservou; 0 se o limite de mensagens ou de tokens do dia já foi atingido
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UsoTutorDia u
               set u.contador = u.contador + 1, u.atualizadoEm = :agora
             where u.usuarioId = :usuarioId and u.data = :data
               and u.contador < :limiteMensagens and u.tokens < :limiteTokens
            """)
    int reservar(@Param("usuarioId") Long usuarioId,
                 @Param("data") LocalDate data,
                 @Param("limiteMensagens") int limiteMensagens,
                 @Param("limiteTokens") long limiteTokens,
                 @Param("agora") LocalDateTime agora);

    /** Devolve a reserva quando nenhum token chegou a ser entregue (falha em todos os provedores). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UsoTutorDia u
               set u.contador = u.contador - 1, u.atualizadoEm = :agora
             where u.usuarioId = :usuarioId and u.data = :data and u.contador > 0
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