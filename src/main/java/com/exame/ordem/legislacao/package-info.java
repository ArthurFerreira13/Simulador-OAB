/**
 * Módulo Legislação: dono das normas, dispositivos, versões imutáveis, documentos-fonte e jobs
 * de ingestão. Contrato público: {@link com.exame.ordem.legislacao.LegislacaoConsulta} e o pacote
 * {@code dto}. {@code internal}, {@code service} e {@code controller} não são consumidos por
 * outros módulos. Este módulo NÃO depende do {@code tutor}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Legislação")
package com.exame.ordem.legislacao;
