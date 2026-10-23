package com.exame.ordem.tutor.dto;

import java.time.LocalDateTime;

/**
 * Mensagem de uma conversa com o tutor.
 *
 * {@code papel} é texto ("USER" | "ASSISTANT") em vez de enum para manter
 * o DTO desacoplado do domínio — mesmo padrão de QuestaoResumo, que expõe
 * disciplina como String.
 *
 * {@code estado}: "COMPLETED" | "INTERRUPTED".
 */
public record MensagemTutorDTO(
        Long id,
        String papel,
        String conteudo,
        String estado,
        LocalDateTime criadaEm
) {}