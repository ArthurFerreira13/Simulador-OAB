package com.exame.ordem.tutor.service;

import com.exame.ordem.system.embedding.EmbeddingErrors;
import com.exame.ordem.tutor.dto.ComentarioPendente;
import com.exame.ordem.tutor.internal.ComentariosPendentesRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Indexa comentários de alternativas em uma tabela pgvector.
 *
 * <p>Parametrizado por (repositório, embedder, store, prefixo de log)
 * para atender Gemini, Ollama e, futuramente, Voyage sem duplicar o
 * laço de indexação.
 *
 * <p><b>Não é {@code @Component}.</b> É instanciado como {@code @Bean}
 * em {@link com.exame.ordem.tutor.config.IndexadorConfig}, uma vez por
 * provedor (Gemini, Ollama). O construtor recebe parâmetros que o
 * Spring não consegue resolver sozinho — há múltiplos {@code EmbeddingModel}
 * e {@code EmbeddingStore} no contexto, e cada instância recebe um par
 * específico. Daí o registro explícito por {@code @Bean}.
 *
 * <p>O lote é processado com Stream: {@code map} chama o provedor,
 * {@code takeWhile} corta no primeiro rate limit — as {@code Resultado}
 * restantes do lote nunca chegam a rodar, o que evita 50 requisições
 * inúteis depois do 429. A query {@code NOT EXISTS} já filtra o que foi
 * indexado, então nova subida retoma exatamente de onde parou.
 */
public class EmbeddingIndexador {

    private static final int LOTE = 50;

    private final Logger log;
    private final ComentariosPendentesRepository pendentes;
    private final EmbeddingModel embedder;
    private final EmbeddingStore<TextSegment> store;
    private final String prefixoLog;

    public EmbeddingIndexador(ComentariosPendentesRepository pendentes,
                              EmbeddingModel embedder,
                              EmbeddingStore<TextSegment> store,
                              String prefixoLog) {
        this.pendentes = pendentes;
        this.embedder = embedder;
        this.store = store;
        this.prefixoLog = prefixoLog;
        this.log = LoggerFactory.getLogger("Indexador" + prefixoLog);
    }

    /**
     * Indexa os comentários ainda sem vetor.
     *
     * <p>A leitura usa cursor por id: um comentário que falha (texto inválido, erro pontual do
     * provedor) é registrado e PULADO nesta execução. Antes, ele continuava pendente e voltava
     * sempre no mesmo lote, e o laço nunca terminava. Os que falharam são retentados na próxima
     * execução.
     */
    public int indexarPendentes() {
        int total = 0;
        int falhas = 0;
        long cursor = 0;
        boolean interrompido = false;

        while (!interrompido) {
            List<ComentarioPendente> lote = pendentes.proximoLote(LOTE, cursor);
            if (lote.isEmpty()) {
                break;
            }
            cursor = lote.get(lote.size() - 1).alternativaId();

            // takeWhile corta assim que um indexarUm devolve "parar" (rate limit).
            List<Resultado> processados = lote.stream()
                    .map(this::indexarUm)
                    .takeWhile(Resultado::continuar)
                    .toList();

            total += (int) processados.stream().filter(Resultado::indexado).count();
            falhas += (int) processados.stream().filter(r -> !r.indexado()).count();

            log.info("{} Progresso: {} indexados, {} falhas pontuais", prefixoLog, total, falhas);

            // takeWhile descarta o item que devolveu "parar": tamanho menor = corte por rate limit.
            interrompido = processados.size() < lote.size();
        }

        if (interrompido) {
            log.warn("{} Interrompido por rate limit. {} indexados até agora. "
                            + "Suba a app novamente para retomar.",
                    prefixoLog, total);
        } else if (falhas > 0) {
            log.warn("{} Concluído com {} comentário(s) não indexados; serão retentados na próxima execução.",
                    prefixoLog, falhas);
        }
        return total;
    }

    private Resultado indexarUm(ComentarioPendente c) {
        try {
            TextSegment seg = toSegment(c);
            Embedding emb = embedder.embed(seg).content();
            store.add(emb, seg);
            return Resultado.sucesso();
        } catch (Exception e) {
            if (EmbeddingErrors.isRateLimit(e)) {
                log.warn("{} Rate limit detectado. Motivo: {}", prefixoLog, e.getMessage());
                return Resultado.parar();
            }
            log.warn("{} Falha ao indexar alternativa {}: {}",
                    prefixoLog, c.alternativaId(), e.getMessage());
            return Resultado.falhaPontual();
        }
    }

    private TextSegment toSegment(ComentarioPendente c) {
        Metadata meta = new Metadata()
                .put("alternativaId", c.alternativaId())
                .put("questaoId", String.valueOf(c.questaoId()))
                .put("letra", c.letra());
        return TextSegment.from(c.comentario(), meta);
    }

    /** indexado = gravou no pgvector; continuar = não foi rate limit. */
    private record Resultado(boolean indexado, boolean continuar) {
        static Resultado sucesso()       { return new Resultado(true,  true); }
        static Resultado parar()         { return new Resultado(false, false); }
        static Resultado falhaPontual()  { return new Resultado(false, true);  }
    }
}