package com.exame.ordem.tutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Provedores de chat ({@code app.tutor.cascata}). A ordem da lista é a prioridade; sem api-key, é ignorado.
 * rpm/rpd/tpm/tpd são tetos locais (0 = sem limite local), aplicados antes de chamar o provedor.
 */
@ConfigurationProperties("app.tutor.cascata")
public record CascataProperties(List<Provedor> provedores) {

    public CascataProperties {
        provedores = provedores == null ? List.of() : List.copyOf(provedores);
    }

    public record Provedor(
            String id,
            String baseUrl,
            String apiKey,
            String model,
            @DefaultValue("0.3") double temperature,
            @DefaultValue("3000") int maxTokens,
            @DefaultValue("45s") Duration timeout,
            @DefaultValue("12s") Duration ttft,
            @DefaultValue("0") int rpm,
            @DefaultValue("0") int rpd,
            @DefaultValue("0") long tpm,
            @DefaultValue("0") long tpd,
            @DefaultValue Map<String, String> headers
    ) {
        public boolean habilitado() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}