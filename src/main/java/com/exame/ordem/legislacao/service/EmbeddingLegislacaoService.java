package com.exame.ordem.legislacao.service;

import com.exame.ordem.legislacao.LegislacaoException;
import com.exame.ordem.legislacao.internal.Contextualizador;
import com.exame.ordem.legislacao.internal.DispositivoPendenteEmbedding;
import com.exame.ordem.legislacao.internal.EmbeddingRepository;
import com.exame.ordem.legislacao.internal.ErrosEmbedding;
import com.exame.ordem.legislacao.internal.NovoEmbedding;
import com.exame.ordem.legislacao.internal.PerfilEmbedding;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gera, de forma idempotente, os embeddings das versões atuais publicadas. É dirigido pelas PENDÊNCIAS
 * (versões sem vetor para o modelo e o pipeline), então serve tanto para a carga inicial quanto para
 * reindexar só o que mudou. Nenhuma chamada ao provedor acontece dentro de transação de banco.
 *
 * <p>Desligado por padrão: {@code app.legislacao.embedding.habilitado=true}. Perfil e modelo em
 * {@code app.legislacao.embedding.perfil} (GEMINI|OLLAMA) e {@code .modelo}. Reaproveita os beans
 * EmbeddingModel já existentes na aplicação (por nome), sem depender de classes do tutor.
 * Estratégia de instância única: o guarda é local ao processo; vetores duplicados são impossíveis
 * (UNIQUE + ON CONFLICT), só haveria chamada repetida ao provedor.
 */
@Service
public class EmbeddingLegislacaoService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingLegislacaoService.class);

    public record ResultadoIndexacao(
            int indexados,
            int falhas,
            boolean interrompidoPorRateLimit,
            int obsoletosDesativados,
            long ativos
    ) {}

    private final EmbeddingRepository repo;
    private final ObjectProvider<EmbeddingModel> gemini;
    private final ObjectProvider<EmbeddingModel> ollama;
    private final boolean habilitado;
    private final PerfilEmbedding perfil;
    private final String modelo;
    private final int tamanhoLote;
    private final AtomicBoolean rodando = new AtomicBoolean(false);

    EmbeddingLegislacaoService(
            EmbeddingRepository repo,
            @Qualifier("geminiEmbeddingModel") ObjectProvider<EmbeddingModel> gemini,
            @Qualifier("ollamaEmbeddingModel") ObjectProvider<EmbeddingModel> ollama,
            @Value("${app.legislacao.embedding.habilitado:false}") boolean habilitado,
            @Value("${app.legislacao.embedding.perfil:GEMINI}") PerfilEmbedding perfil,
            @Value("${app.legislacao.embedding.modelo:}") String modelo,
            @Value("${app.legislacao.embedding.lote:32}") int tamanhoLote) {
        this.repo = Objects.requireNonNull(repo, "repo não pode ser nulo");
        this.gemini = Objects.requireNonNull(gemini, "gemini não pode ser nulo");
        this.ollama = Objects.requireNonNull(ollama, "ollama não pode ser nulo");
        this.habilitado = habilitado;
        this.perfil = Objects.requireNonNull(perfil, "perfil não pode ser nulo");
        this.modelo = (modelo == null || modelo.isBlank()) ? perfil.modeloPadrao() : modelo;
        this.tamanhoLote = Math.max(1, Math.min(tamanhoLote, 100));
    }

    /** Varredura periódica; barata quando não há pendências. */
    @Scheduled(fixedDelayString = "${app.legislacao.embedding.intervalo:PT5M}", initialDelayString = "PT1M")
    void varrer() {
        if (!habilitado) {
            return;
        }
        try {
            ResultadoIndexacao r = indexarPendentes();
            if (r.indexados() > 0 || r.falhas() > 0 || r.obsoletosDesativados() > 0) {
                log.info("Embeddings da legislação: {}", r);
            }
        } catch (LegislacaoException e) {
            log.debug("Varredura de embeddings ignorada: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Falha na varredura de embeddings da legislação: {}", e.getMessage());
        }
    }

    public ResultadoIndexacao indexarPendentes() {
        EmbeddingModel model = resolverModelo();
        if (model == null) {
            throw new LegislacaoException("EmbeddingModel do perfil " + perfil + " não está disponível.");
        }
        if (!rodando.compareAndSet(false, true)) {
            throw new LegislacaoException("Indexação já em andamento.");
        }
        try {
            int obsoletos = repo.desativarObsoletos(perfil);
            int indexados = 0;
            int falhas = 0;
            boolean interrompidoPorRateLimit = false;
            UUID cursor = EmbeddingRepository.INICIO;

            while (!interrompidoPorRateLimit) {
                List<DispositivoPendenteEmbedding> lote = repo.pendentes(
                        perfil, modelo, Contextualizador.VERSAO, cursor, tamanhoLote
                );
                if (lote.isEmpty()) {
                    break;
                }
                cursor = lote.get(lote.size() - 1).versaoId();

                List<String> textos = new ArrayList<>(lote.size());
                for (DispositivoPendenteEmbedding p : lote) {
                    textos.add(Contextualizador.montar(p.nomeNorma(), p.caminho(), p.textoPai(), p.texto()));
                }

                List<Embedding> vetores = new ArrayList<>(lote.size());
                try {
                    vetores.addAll(model.embedAll(textos.stream().map(TextSegment::from).toList()).content());
                } catch (RuntimeException e) {
                    if (ErrosEmbedding.ehRateLimit(e)) {
                        log.warn("Rate limit do provedor de embedding; indexação interrompida: {}", e.getMessage());
                        interrompidoPorRateLimit = true;
                        break;
                    }
                    // Falha no lote: isola o item problemático tentando um a um.
                    vetores.clear();
                    for (String t : textos) {
                        try {
                            vetores.add(embedirUm(model, t));
                        } catch (RuntimeException itemRateLimit) {
                            if (ErrosEmbedding.ehRateLimit(itemRateLimit)) {
                                log.warn("Rate limit durante fallback item a item; indexação interrompida: {}",
                                        itemRateLimit.getMessage());
                                interrompidoPorRateLimit = true;
                                break;
                            }
                            throw itemRateLimit;
                        }
                    }
                }

                List<NovoEmbedding> gravar = new ArrayList<>(lote.size());
                for (int i = 0; i < vetores.size() && i < lote.size(); i++) {
                    Embedding emb = vetores.get(i);
                    if (emb == null || emb.vector().length != PerfilEmbedding.DIMENSAO) {
                        falhas++;
                        continue;
                    }
                    DispositivoPendenteEmbedding p = lote.get(i);
                    gravar.add(new NovoEmbedding(
                            p.versaoId(),
                            p.normaId(),
                            modelo,
                            null,
                            Contextualizador.VERSAO,
                            Contextualizador.hash(modelo, Contextualizador.VERSAO, textos.get(i)),
                            textos.get(i),
                            emb.vector()
                    ));
                }

                if (interrompidoPorRateLimit && vetores.size() < lote.size()) {
                    falhas += (lote.size() - vetores.size());
                }

                indexados += repo.inserir(perfil, gravar);
            }

            return new ResultadoIndexacao(
                    indexados,
                    falhas,
                    interrompidoPorRateLimit,
                    obsoletos,
                    repo.contarAtivos(perfil)
            );
        } finally {
            rodando.set(false);
        }
    }

    private EmbeddingModel resolverModelo() {
        return (perfil == PerfilEmbedding.GEMINI ? gemini : ollama).getIfAvailable();
    }

    private Embedding embedirUm(EmbeddingModel model, String texto) {
        try {
            return model.embed(texto).content();
        } catch (RuntimeException e) {
            if (ErrosEmbedding.ehRateLimit(e)) {
                throw e;
            }
            log.warn("Item não indexado (falha do provedor): {}", e.getMessage());
            return null;
        }
    }
}