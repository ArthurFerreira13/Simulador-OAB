package com.exame.ordem.tutor;

import com.exame.ordem.tutor.service.EmbeddingIndexador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Roda a indexação do Gemini 1× quando a app sobe.
 *
 * <p>Controlado pela flag {@code GEMINI_INDEXADOR_HABILITADO} no .env:
 * <ul>
 *   <li>{@code true}  → indexa os pendentes ao subir</li>
 *   <li>{@code false} → não faz nada (padrão)</li>
 * </ul>
 *
 * <p>Padrão é {@code false} porque, com a cota gratuita do Gemini
 * estourada (429), não faz sentido tentar indexar a cada subida. A
 * proteção de rate limit no {@code EmbeddingIndexador} para o loop,
 * mas ainda assim é trabalho jogado fora. Quando a cota renovar, liga
 * a flag, roda uma vez, desliga.
 */
@Component
@ConditionalOnProperty(
        name = "app.tutor.gemini-indexador.habilitado",
        havingValue = "true",
        matchIfMissing = false)
class IndexadorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexadorRunner.class);

    private final EmbeddingIndexador indexador;

    IndexadorRunner(@Qualifier("geminiIndexador") EmbeddingIndexador indexador) {
        this.indexador = indexador;
    }

    @Override
    public void run(String... args) {
        log.info("[Gemini] Verificando comentários pendentes de indexação...");
        int total = indexador.indexarPendentes();
        log.info("[Gemini] {} comentários indexados", total);
    }
}