package com.exame.ordem;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

 class ApplicationTests {

    private final ApplicationModules modules =
            ApplicationModules.of(OrdemApplication.class);

    @Test
    void writeDocumentationSnippets() {

        // Verifica a estrutura dos módulos:
        // ciclos, dependências internas, etc.
        modules.verify();

        // Gera os diagramas PlantUML:
        // visão geral + módulos individuais.
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}