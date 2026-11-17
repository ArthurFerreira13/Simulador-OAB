package com.exame.ordem.tutor;

import com.exame.ordem.tutor.service.EmbeddingIndexador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;

/**
 * Roda a indexação de comentários pendentes quando a aplicação sobe.
 *
 * <p>Substitui {@code IndexadorRunner} (Gemini) e {@code OllamaIndexadorRunner}, que eram cópias
 * uma da outra. Roda todos os {@link EmbeddingIndexador} que existirem no contexto. Cada um só existe
 * quando a flag do provedor está ligada ({@code app.tutor.gemini-indexador.habilitado} ou
 * {@code app.tutor.ollama-indexador.habilitado}); sem nenhuma flag, este componente não faz nada.
 *
 * <p>A indexação roda em uma virtual thread, depois do {@code ApplicationReadyEvent}, para não
 * atrasar a subida (ping de provedores, launcher do FreeLLMAPI). Os provedores rodam em sequência,
 * um por vez. Atenção: indexar compete com o tráfego dos alunos pelo mesmo Ollama; mantenha as flags
 * desligadas em produção e ligue só quando não houver uso.
 */
@Component
class IndexadoresRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexadoresRunner.class);

    private final ListableBeanFactory beans;

    IndexadoresRunner(ListableBeanFactory beans) {
        this.beans = beans;
    }

    @EventListener(ApplicationReadyEvent.class)
    void iniciar() {
        // TreeMap: ordem estável (gemini antes de ollama) nos logs.
        Map<String, EmbeddingIndexador> indexadores =
                new TreeMap<>(beans.getBeansOfType(EmbeddingIndexador.class));
        if (indexadores.isEmpty()) {
            return;
        }
        Thread.ofVirtual().name("indexadores").start(() -> executar(indexadores));
    }

    private void executar(Map<String, EmbeddingIndexador> indexadores) {
        indexadores.forEach((nome, indexador) -> {
            try {
                log.info("[{}] Verificando comentários pendentes de indexação...", nome);
                int total = indexador.indexarPendentes();
                log.info("[{}] {} comentários indexados", nome, total);
            } catch (Exception e) {
                log.error("[{}] Falha na indexação: {}", nome, e.getMessage(), e);
            }
        });
    }
}