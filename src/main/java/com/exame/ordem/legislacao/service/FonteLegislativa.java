package com.exame.ordem.legislacao.service;

import java.nio.file.Path;
import java.util.Objects;

public interface FonteLegislativa {

    record ResultadoColeta(String conteudo, String urlEfetiva) {
        public ResultadoColeta {
            Objects.requireNonNull(conteudo, "conteudo não pode ser nulo");
        }
    }

    ResultadoColeta obterConteudo(String urlExterna, Path arquivoFallbackLocal, String chaveCache);
}