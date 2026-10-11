package com.exame.ordem.questoes.dto;

import java.time.LocalDateTime;

/**
 * Status de uma questão para um usuário específico.
 *
 * Usado para:
 *  - retornar o resultado de uma resposta avulsa
 *  - preencher o campo "status" em listagens (RF02.1)
 *
 * Sempre que o usuário não respondeu a questão, todos os campos
 * ficam nulos, exceto "respondida" que fica false.
 */
public record StatusQuestaoUsuario(
        boolean respondida,
        Boolean correta,
        Long alternativaEscolhidaId,
        LocalDateTime respondidoEm
) {
    public static StatusQuestaoUsuario naoRespondida() {
        return new StatusQuestaoUsuario(false, null, null, null);
    }
}