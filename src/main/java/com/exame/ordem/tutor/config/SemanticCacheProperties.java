package com.exame.ordem.tutor.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do cache semântico do tutor (Camada 2 da arquitetura
 * econômica, Relatório Técnico §5.2).
 *
 * <p><b>{@code enabled}:</b> permite desligar o cache sem remover o
 * código. Quando {@code false}, {@code SemanticCacheService.buscar}
 * sempre devolve {@code Optional.empty()} e {@code armazenar} é no-op.
 * Útil durante testes de carga ou quando o {@code EmbeddingModel}
 * estiver indisponível.
 *
 * <p><b>{@code similarityThreshold}:</b> similaridade mínima de cosseno
 * para reutilizar uma resposta. Default {@code 0.92} é conservador
 * (conteúdo jurídico, risco de alucinação). O relatório sugere três
 * faixas: {@code >=0.92} reutiliza, {@code 0.80–0.92} RAG curto,
 * {@code <0.80} LLM. Esta propriedade cobre só a primeira faixa — as
 * demais ficam no fluxo do {@code TutorIaServiceImpl}.
 *
 * <p><b>{@code promptVersion}:</b> string que entra na chave lógica de
 * invalidação. Se o system prompt do {@code TutorAiAssistant} mudar
 * (novas diretrizes, correção de comportamento), basta incrementar
 * esta string para que entradas antigas deixem de acertar. Default
 * {@code "v1"}; o dono do projeto decide quando bumpar.
 *
 * <p><b>{@code ttlDays}:</b> idade máxima de uma entrada antes de ser
 * considerada elegível para limpeza. Não há job de limpeza automática
 * nesta etapa — o campo existe para o job futuro (Fase 3 do relatório)
 * e para observação.
 */
@Validated
@ConfigurationProperties("app.tutor.semantic-cache")
public record SemanticCacheProperties(

        @DefaultValue("true")
        boolean enabled,

        @DecimalMin("0.50") @DecimalMax("1.00") @DefaultValue("0.92")
        double similarityThreshold,

        @NotBlank @DefaultValue("v1")
        String promptVersion,

        @Positive @DefaultValue("30")
        int ttlDays
) {}