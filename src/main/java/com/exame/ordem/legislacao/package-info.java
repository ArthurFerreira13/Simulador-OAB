/**
 * Módulo Legislação: dono de normas, versões, artigos e embeddings jurídicos.
 *
 * <p>API pública: {@link LegislacaoQuery} e o pacote {@code dto}.
 * Nenhum outro módulo deve acessar {@code internal}. Este módulo NÃO depende do {@code tutor}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Legislação",
        allowedDependencies = {"system"}
)
package com.exame.ordem.legislacao;
