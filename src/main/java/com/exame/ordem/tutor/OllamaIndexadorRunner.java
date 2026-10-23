package com.exame.ordem.tutor;

import com.exame.ordem.tutor.service.EmbeddingIndexador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Roda a indexação do Ollama 1× quando a app sobe.
 *
 * <p>Controlado pela flag {@code OLLAMA_INDEXADOR_HABILITADO} no .env:
 * <ul>
 *   <li>{@code true}  → indexa os pendentes ao subir</li>
 *   <li>{@code false} → não faz nada (padrão)</li>
 * </ul>
 *
 * <p>Depois que a tabela {@code comentario_embedding_ollama} estiver
 * completa (6.080 linhas), deixa a flag em {@code false} — o runner
 * não precisa mais rodar, e a query de pendentes voltaria vazia de
 * qualquer forma.
 */
@Component
@ConditionalOnProperty(
        name = "app.tutor.ollama-indexador.habilitado",
        havingValue = "true",
        matchIfMissing = false)
class OllamaIndexadorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OllamaIndexadorRunner.class);

    private final EmbeddingIndexador indexador;

    OllamaIndexadorRunner(@Qualifier("ollamaIndexador") EmbeddingIndexador indexador) {
        this.indexador = indexador;
    }

    @Override
    public void run(String... args) {
        log.info("[Ollama] Verificando comentários pendentes de indexação...");
        int total = indexador.indexarPendentes();
        log.info("[Ollama] {} comentários indexados", total);
    }
}