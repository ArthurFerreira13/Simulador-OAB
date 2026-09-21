package com.exame.ordem.questoes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumeroProvaExtractorTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "'43º EXAME DE ORDEM UNIFICADO - PROVA TIPO 1 (BRANCA)', 43",
            "'40º EXAME DE ORDEM UNIFICADO - PROVA TIPO 1 (BRANCA)', 40",
            "'29° EXAME DE ORDEM UNIFICADO - PROVA TIPO 1 (BRANCA)', 29",
            "'1º EXAME', 1",
            "'99 EXAME', 99"
    })
    @DisplayName("extrai o número do início do título")
    void extraiNumero(String titulo, int esperado) {
        assertThat(NumeroProvaExtractor.extrair(titulo)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "EXAME SEM NÚMERO",
            "abc 43 exame"
    })
    @DisplayName("retorna null quando não há número no início")
    void semNumeroRetornaNull(String titulo) {
        assertThat(NumeroProvaExtractor.extrair(titulo)).isNull();
    }

    @Test
    @DisplayName("extrair(null) lança NPE (contrato: título nunca é null — DB NOT NULL)")
    void tituloNullLancaNpe() {
        assertThatThrownBy(() -> NumeroProvaExtractor.extrair(null))
                .isInstanceOf(NullPointerException.class);
    }
}