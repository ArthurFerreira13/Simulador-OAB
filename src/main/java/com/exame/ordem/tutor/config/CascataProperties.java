package com.exame.ordem.tutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
            @DefaultValue Map<String, String> headers,
            String pingUrl // [NOVO] Opcional: URL customizada para ping
    ) {
        public boolean habilitado() {
            return apiKey != null && !apiKey.isBlank();
        }

        // Retorna a URL de ping customizada ou a padrão (/models)
        public String urlParaPing() {
            return (pingUrl != null && !pingUrl.isBlank()) ? pingUrl : baseUrl() + "/models";
        }
    }
}