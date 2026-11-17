package com.exame.ordem.tutor.config;

import com.exame.ordem.tutor.internal.ProvedorEmbedding;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Validated
@ConfigurationProperties("app.tutor.rag")
public record RagProperties(
        @Positive @DefaultValue("3") int topKSemelhantes,
        @Positive @DefaultValue("12") int candidatos,
        @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.75") double minScoreGemini,
        @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.70") double minScoreOllama,
        @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.70") double minScoreFreellm,
        @DefaultValue("true") boolean ollamaHabilitado,
        @DefaultValue({"ERRADO", "SUSPEITO"}) List<String> statusBloqueados,
        @Positive @DefaultValue("1200") int maxCaracteresPorFonte
) {

    public RagProperties {
        statusBloqueados = statusBloqueados == null
                ? List.of()
                : statusBloqueados.stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    @AssertTrue(message = "app.tutor.rag.candidatos deve ser maior ou igual a topKSemelhantes")
    public boolean isCandidatosConsistente() {
        return candidatos >= topKSemelhantes;
    }

    public double minScore(ProvedorEmbedding provedor) {
        return switch (provedor) {
            case GEMINI -> minScoreGemini;
            case OLLAMA -> minScoreOllama;
            case FREELLM -> minScoreFreellm;
        };
    }

    public Set<String> statusBloqueadosSet() {
        return Set.copyOf(statusBloqueados);
    }

    public boolean isStatusBloqueado(String status) {
        return status != null && statusBloqueados.contains(status.trim().toUpperCase(Locale.ROOT));
    }
}