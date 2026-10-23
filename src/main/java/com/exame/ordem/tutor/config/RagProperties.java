package com.exame.ordem.tutor.config;

import com.exame.ordem.tutor.internal.ProvedorEmbedding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Parâmetros da busca RAG ({@code app.tutor.rag}). O pgvector usa o mesmo {@code DataSource} do Spring.
 *
 * @param topKSemelhantes máximo de comentários de outras questões enviados ao LLM
 * @param candidatos quantidade de vizinhos solicitada ao pgvector antes do filtro
 * @param minScoreGemini score mínimo para o provedor Gemini
 * @param minScoreOllama score mínimo para o provedor Ollama
 * @param ollamaHabilitado se o Ollama entra na busca RAG (desligue em produção)
 * @param statusBloqueados status que nunca devem entrar no prompt
 * @param maxCaracteresPorFonte limite de caracteres por comentário
 */
@ConfigurationProperties("app.tutor.rag")
public record RagProperties(
        @DefaultValue("3") int topKSemelhantes,
        @DefaultValue("12") int candidatos,
        @DefaultValue("0.75") double minScoreGemini,
        @DefaultValue("0.70") double minScoreOllama,
        @DefaultValue("true") boolean ollamaHabilitado,
        @DefaultValue({"ERRADO", "SUSPEITO"}) List<String> statusBloqueados,
        @DefaultValue("1200") int maxCaracteresPorFonte
) {

    public double minScore(ProvedorEmbedding provedor) {
        return switch (provedor) {
            case GEMINI -> minScoreGemini;
            case OLLAMA -> minScoreOllama;
        };
    }
}