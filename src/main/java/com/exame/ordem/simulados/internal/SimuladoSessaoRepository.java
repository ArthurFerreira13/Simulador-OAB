package com.exame.ordem.simulados.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SimuladoSessaoRepository extends JpaRepository<SimuladoSessao, Long> {

    Optional<SimuladoSessao> findByUsuarioIdAndStatus(Long usuarioId, StatusSessao status);

    Optional<SimuladoSessao> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioIdAndIniciadoEmAfter(Long usuarioId, LocalDateTime instante);

    /**
     * Conta quantas sessões do usuário iniciadas após {@code instante}
     * possuem ao menos uma resposta registrada (D13/RN14).
     *
     * Sessão abandonada sem nenhuma resposta não conta no limite diário.
     */
    @Query("""
        SELECT COUNT(s) FROM SimuladoSessao s
        WHERE s.usuarioId = :usuarioId
          AND s.iniciadoEm >= :inicio
          AND EXISTS (
              SELECT 1 FROM RespostaSimulado r WHERE r.sessaoId = s.id
          )
        """)
    long countComRespostaByUsuarioIdAndIniciadoEmAfter(
            @Param("usuarioId") Long usuarioId,
            @Param("inicio") LocalDateTime inicio);
}