package com.exame.ordem.tutor.service;

import com.exame.ordem.system.embedding.EmbeddingErrors;
import com.exame.ordem.tutor.config.CacheConfig;
import com.exame.ordem.tutor.config.RagProperties;
import com.exame.ordem.tutor.internal.ComentariosRepository;
import com.exame.ordem.tutor.internal.FonteRag;
import com.exame.ordem.tutor.internal.ProvedorEmbedding;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RagProviderResolver {

    private static final Logger log = LoggerFactory.getLogger(RagProviderResolver.class);

    private record Par(ProvedorEmbedding provedor, EmbeddingModel model, EmbeddingStore<TextSegment> store) {}

    private final List<Par> pares;
    private final ComentariosRepository comentarios;
    private final ComentariosDaQuestaoService comentariosDaQuestao;
    private final RagProperties props;
    private final Cache embeddingCache;
    private final EmbeddingGate gate;

    RagProviderResolver(
            @Qualifier("geminiEmbeddingModel") ObjectProvider<EmbeddingModel> geminiModelProvider,
            @Qualifier("geminiEmbeddingStore") ObjectProvider<EmbeddingStore<TextSegment>> geminiStoreProvider,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaModel,
            @Qualifier("ollamaEmbeddingStore") EmbeddingStore<TextSegment> ollamaStore,
            ComentariosRepository comentarios,
            ComentariosDaQuestaoService comentariosDaQuestao,
            RagProperties props,
            CacheManager cacheManager,
            EmbeddingGate gate) {

        List<Par> configurados = new ArrayList<>(2);
        if (props.ollamaHabilitado()) {
            configurados.add(new Par(ProvedorEmbedding.OLLAMA, ollamaModel, ollamaStore));
        }
        EmbeddingModel geminiModel = geminiModelProvider.getIfAvailable();
        EmbeddingStore<TextSegment> geminiStore = geminiStoreProvider.getIfAvailable();
        if (geminiModel != null && geminiStore != null) {
            configurados.add(new Par(ProvedorEmbedding.GEMINI, geminiModel, geminiStore));
        }
        this.pares = List.copyOf(configurados);

        this.comentarios = comentarios;
        this.comentariosDaQuestao = comentariosDaQuestao;
        this.props = props;
        this.embeddingCache = cacheManager.getCache(CacheConfig.RAG_EMBEDDING);
        this.gate = gate;
    }

    public List<FonteRag> buscar(String pergunta, Integer questaoId) {
        long inicioBusca = System.currentTimeMillis();

        List<FonteRag> daQuestao = comentariosDaQuestao.buscar(questaoId);
        List<FonteRag> semelhantes = buscarSemelhantes(pergunta, questaoId, daQuestao);

        log.debug("RAG completo levou {} ms.", System.currentTimeMillis() - inicioBusca);

        List<FonteRag> todas = new ArrayList<>(daQuestao.size() + semelhantes.size());
        todas.addAll(daQuestao);
        todas.addAll(semelhantes);
        return List.copyOf(todas);
    }

    private List<FonteRag> buscarSemelhantes(String pergunta, Integer questaoId, List<FonteRag> jaIncluidas) {
        if (pares.isEmpty()) {
            return List.of();
        }

        Set<Long> excluir = jaIncluidas.stream()
                .map(FonteRag::alternativaId)
                .collect(Collectors.toSet());

        for (Par par : pares) {
            try {
                return buscarNoStore(par, pergunta, questaoId, excluir);
            } catch (Exception e) {
                if (EmbeddingErrors.isRateLimit(e)) {
                    log.warn("{} Rate limit (429) na busca RAG; tentando próximo provedor.", par.provedor().prefixoLog());
                } else {
                    log.warn("{} Erro na busca RAG ({}); tentando próximo provedor.", par.provedor().prefixoLog(), e.getMessage());
                }
            }
        }
        log.error("RAG indisponível em todos os provedores; o tutor responderá sem comentários semelhantes.");
        return List.of();
    }

    private List<FonteRag> buscarNoStore(Par par, String pergunta, Integer questaoId, Set<Long> excluir) {
        long inicio = System.currentTimeMillis();
        Embedding consulta = obterEmbeddingComCache(par, pergunta);
        long tempoEmbedding = System.currentTimeMillis() - inicio;

        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(consulta)
                .maxResults(props.candidatos())
                .minScore(props.minScore(par.provedor()))
                .build();

        long inicioDb = System.currentTimeMillis();
        List<EmbeddingMatch<TextSegment>> matches = par.store().search(request).matches();
        long tempoDb = System.currentTimeMillis() - inicioDb;

        Map<Long, Double> scores = new LinkedHashMap<>();
        for (EmbeddingMatch<TextSegment> m : matches) {
            Long id = alternativaId(m.embedded());
            if (id != null && !excluir.contains(id)) {
                scores.putIfAbsent(id, m.score());
            }
        }

        Map<Long, FonteRag> atuais = comentarios.atuais(scores.keySet(), props.statusBloqueados());

        List<FonteRag> resultado = new ArrayList<>();
        for (Map.Entry<Long, Double> e : scores.entrySet()) {
            FonteRag atual = atuais.get(e.getKey());
            if (atual == null || questaoId.equals(atual.questaoId())) {
                continue;
            }
            resultado.add(atual.comScore(e.getValue()));
            if (resultado.size() >= props.topKSemelhantes()) {
                break;
            }
        }

        log.debug("RAG {} | Embedding/Cache: {}ms | pgvector: {}ms | Aceitos: {}",
                par.provedor().prefixoLog(), tempoEmbedding, tempoDb, resultado.size());

        return resultado;
    }

    private Embedding obterEmbeddingComCache(Par par, String pergunta) {
        String chave = par.provedor().name() + ":" + normalizar(pergunta);
        if (embeddingCache != null) {
            Embedding emCache = embeddingCache.get(chave, Embedding.class);
            if (emCache != null) {
                return emCache;
            }
        }
        Embedding gerado = gate.run(() -> par.model().embed(pergunta).content());
        if (embeddingCache != null && gerado != null) {
            embeddingCache.put(chave, gerado);
        }
        return gerado;
    }

    private static String normalizar(String s) {
        if (s == null) return "";
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", " ").trim();
    }

    private static Long alternativaId(TextSegment segmento) {
        if (segmento == null) return null;
        Object bruto = segmento.metadata().toMap().get("alternativaId");
        if (bruto == null) return null;
        try {
            return Long.valueOf(String.valueOf(bruto));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}