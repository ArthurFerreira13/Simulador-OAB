package com.exame.ordem.tutor.dto;

/**
 * Alternativa com comentário ainda não indexada em uma tabela pgvector.
 *
 * <p>Record imutável — projeção de leitura do repositório. Não tem
 * setter porque o indexador só lê; a escrita acontece no
 * {@code EmbeddingStore}, não aqui.
 */
public record ComentarioPendente(
        Long alternativaId,
        String comentario,
        Integer questaoId,
        String letra
) {}