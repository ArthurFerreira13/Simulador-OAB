package com.exame.ordem.system.erro;

import java.time.Instant;
import java.util.List;

/**
 * Corpo padrão de erro da API.
 *
 * @param codigo     código estável (enum) — o front decide o que fazer
 * @param mensagem   texto legível pra exibir ao usuário
 * @param path       caminho da requisição que falhou
 * @param timestamp  momento do erro (UTC)
 * @param detalhes   lista opcional de detalhes (ex.: erros de validação)
 */
public record ErroDTO(
        ErroCodigo codigo,
        String mensagem,
        String path,
        Instant timestamp,
        List<String> detalhes
) {
    public ErroDTO(ErroCodigo codigo, String mensagem, String path) {
        this(codigo, mensagem, path, Instant.now(), List.of());
    }
}