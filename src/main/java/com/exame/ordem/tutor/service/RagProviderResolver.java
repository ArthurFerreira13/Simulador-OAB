package com.exame.ordem.tutor.service;

import com.exame.ordem.system.embedding.EmbeddingErrors;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Monta as fontes (comentários) que o tutor usa para responder.
 *
 * <p>Duas origens, nesta ordem:
 * <ol>
 *   <li><b>QUESTAO</b> - comentários das alternativas da própria questão, por consulta exata
 *       ao banco. Não depende de embedding, então não sofre com cota, limite de similaridade
 *       nem com o filtro-depois-do-HNSW do pgvector.</li>
 *   <li><b>SEMELHANTE</b> - comentários de outras questões, por busca vetorial. Tenta o Ollama
 *       primeiro (rápido e local) e, em caso de falha, cai no Gemini.</li>
 * </ol>
 *
 * <p>Em ambas as origens o texto vem de {@code alternativas} (versão atual) e comentários com
 * status de triagem bloqueado (ERRADO/SUSPEITO) são descartados. Se tudo falhar, devolve o que
 * houver (possivelmente vazio) e o tutor responde sem fonte, sem derrubar a requisição.
 */
@Service
public class RagProviderResolver {

    private static final Logger log = LoggerFactory.getLogger(RagProviderResolver.class);

    private record Par(ProvedorEmbedding provedor, EmbeddingModel model, EmbeddingStore<TextSegment> store) {}

    private final List<Par> pares;
    private final ComentariosRepository comentarios;
    private final RagProperties props;

    RagProviderResolver(
            @Qualifier("geminiEmbeddingModel") EmbeddingModel geminiModel,
            @Qualifier("geminiEmbeddingStore") EmbeddingStore<TextSegment> geminiStore,
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaModel,
            @Qualifier("ollamaEmbeddingStore") EmbeddingStore<TextSegment> ollamaStore,
            ComentariosRepository comentarios,
            RagProperties props) {

        // ALTERAÇÃO AQUI: Ollama primeiro para evitar o timeout de 2 minutos do Gemini (429)
        this.pares = List.of(
                new Par(ProvedorEmbedding.OLLAMA, ollamaModel, ollamaStore),
                new Par(ProvedorEmbedding.GEMINI, geminiModel, geminiStore));

        this.comentarios = comentarios;
        this.props = props;
    }

    public List<FonteRag> buscar(String pergunta, Integer questaoId) {
        List<FonteRag> daQuestao = comentariosDaQuestao(questaoId);
        List<FonteRag> semelhantes = buscarSemelhantes(pergunta, questaoId, daQuestao);

        List<FonteRag> todas = new ArrayList<>(daQuestao);
        todas.addAll(semelhantes);
        return List.copyOf(todas);
    }

    private List<FonteRag> comentariosDaQuestao(Integer questaoId) {
        try {
            return comentarios.daQuestao(questaoId, props.statusBloqueados());
        } catch (Exception e) {
            log.error("Falha ao ler comentários da questão {}: {}", questaoId, e.getMessage());
            return List.of();
        }
    }

    private List<FonteRag> buscarSemelhantes(String pergunta, Integer questaoId, List<FonteRag> jaIncluidas) {
        Set<Long> excluir = jaIncluidas.stream().map(FonteRag::alternativaId).collect(Collectors.toSet());

        for (Par par : pares) {
            try {
                return buscarNoStore(par, pergunta, questaoId, excluir);
            } catch (Exception e) {
                if (EmbeddingErrors.isRateLimit(e)) {
                    log.warn("{} Rate limit (429) na busca RAG; tentando o próximo provedor.",
                            par.provedor().prefixoLog());
                } else {
                    log.warn("{} Erro na busca RAG: {}; tentando o próximo provedor.",
                            par.provedor().prefixoLog(), e.getMessage());
                }
            }
        }
        log.error("RAG indisponível em todos os provedores; o tutor responderá sem comentários semelhantes.");
        return List.of();
    }

    private List<FonteRag> buscarNoStore(Par par, String pergunta, Integer questaoId, Set<Long> excluir) {
        Embedding consulta = par.model().embed(pergunta).content();
        double minScore = props.minScore(par.provedor());

        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(consulta)
                .maxResults(props.candidatos())
                .minScore(minScore)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = par.store().search(request).matches();

        // alternativaId -> score, na ordem devolvida pelo store (maior similaridade primeiro)
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (EmbeddingMatch<TextSegment> m : matches) {
            Long id = alternativaId(m.embedded());
            if (id != null && !excluir.contains(id)) {
                scores.putIfAbsent(id, m.score());
            }
        }

        // Texto atual + triagem: descarta ERRADO/SUSPEITO e usa o comentário corrigido, não o do vetor.
        Map<Long, FonteRag> atuais = comentarios.atuais(scores.keySet(), props.statusBloqueados());

        List<FonteRag> resultado = new ArrayList<>();
        for (Map.Entry<Long, Double> e : scores.entrySet()) {
            FonteRag atual = atuais.get(e.getKey());
            if (atual == null || questaoId.equals(atual.questaoId())) {
                continue;
            }
            resultado.add(new FonteRag(atual.alternativaId(), atual.questaoId(), atual.letra(),
                    atual.texto(), FonteRag.Origem.SEMELHANTE, e.getValue()));
            if (resultado.size() >= props.topKSemelhantes()) {
                break;
            }
        }

        // Linha de calibração do limite de relevância (item 2): quantos vizinhos passaram e qual o melhor.
        log.info("RAG {} candidatos={} aposTriagem={} aceitos={} minScore={} melhorScore={}",
                par.provedor().prefixoLog(), matches.size(), atuais.size(), resultado.size(), minScore,
                matches.isEmpty() ? "n/a" : String.format("%.3f", matches.get(0).score()));
        return resultado;
    }

    /** Os metadados são gravados como texto pelo PgVectorEmbeddingStore; aceita número por segurança. */
    private static Long alternativaId(TextSegment segmento) {
        if (segmento == null) {
            return null;
        }
        Object bruto = segmento.metadata().toMap().get("alternativaId");
        if (bruto == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(bruto));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}