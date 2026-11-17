package com.exame.ordem.tutor.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Provedores do roteador de LLM.
 *
 * <p><b>Apesar do nome histórico "cascata", o roteador não tenta os
 * provedores em ordem até um "OK".</b> O {@code LlmRouter} dispara o
 * primeiro provedor; se ele não emitir token em {@code hedgeDelay}, um
 * segundo é disparado em paralelo. O primeiro que emitir token vence
 * (race/hedge). Erros acionam o próximo da fila sequencialmente.
 *
 * <p><b>{@code hedgeDelay}:</b> tempo até disparar o segundo provedor
 * em paralelo. Default {@code 4500ms}. Trade-off: valores baixos
 * melhoram latência percebida, mas aumentam custo de token (o perdedor
 * da corrida não é cancelável — ver Javadoc do {@code LlmRouter}).
 * Idealmente alinhado com {@code Provedor.ttft} do primário; se
 * {@code ttft=12s} e {@code hedgeDelay=4.5s}, o hedge dispara antes do
 * TTFT prometido, o que é a intenção (privilegiar latência).
 *
 * <p><b>{@code @Valid} no argumento de tipo:</b> o Hibernate Validator 9
 * (padrão no Boot 4.1) emite HV000271 quando {@code @Valid} está no
 * container ({@code List<Provedor>}) em vez do argumento
 * ({@code List<@Valid Provedor>}). A semântica é a mesma (cascateia
 * validação para cada item), mas a forma no argumento é a recomendada
 * e não gera warning no boot.
 *
 * <p><b>{@code apiKey}:</b> intencionalmente não obrigatória. Provedores
 * locais (ex.: freellmapi, ollama) legitimamente não têm chave; quem
 * decide se o provedor está utilizável é {@link Provedor#habilitado()}.
 */
@Validated
@ConfigurationProperties("app.tutor.cascata")
public record CascataProperties(

        @NestedConfigurationProperty
        List<@Valid Provedor> provedores,

        @DurationMin(millis = 1)
        @DefaultValue("4500ms")
        Duration hedgeDelay
) {

    public CascataProperties {
        provedores = provedores == null ? List.of() : List.copyOf(provedores);
    }

    public record Provedor(
            @NotBlank String id,
            @NotBlank String baseUrl,
            String apiKey,
            @NotBlank String model,
            @DecimalMin("0.0") @DecimalMax("2.0") @DefaultValue("0.3") double temperature,
            @Positive @DefaultValue("3000") int maxTokens,
            @DurationMin(millis = 1) @DefaultValue("45s") Duration timeout,
            @DurationMin(millis = 1) @DefaultValue("12s") Duration ttft,
            @PositiveOrZero @DefaultValue("0") int rpm,
            @PositiveOrZero @DefaultValue("0") int rpd,
            @PositiveOrZero @DefaultValue("0") long tpm,
            @PositiveOrZero @DefaultValue("0") long tpd,
            @DefaultValue Map<String, String> headers,
            String pingUrl
    ) {
        public boolean habilitado() {
            return apiKey != null && !apiKey.isBlank();
        }

        public String urlParaPing() {
            return (pingUrl != null && !pingUrl.isBlank()) ? pingUrl : baseUrl + "/models";
        }
    }
}