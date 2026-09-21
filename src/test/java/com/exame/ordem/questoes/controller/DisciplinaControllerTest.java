package com.exame.ordem.questoes.controller;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.DisciplinaResumo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DisciplinaController.class)
class DisciplinaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QuestoesQueryService service;

    @Test
    @DisplayName("GET /api/disciplinas → 200 com lista de disciplinas")
    void listar() throws Exception {
        when(service.listarDisciplinas()).thenReturn(List.of(
                new DisciplinaResumo(1L, "Ética e Estatuto da Advocacia"),
                new DisciplinaResumo(2L, "Constitucional")
        ));

        mockMvc.perform(get("/api/disciplinas"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Ética e Estatuto da Advocacia"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].nome").value("Constitucional"));
    }

    @Test
    @DisplayName("GET /api/disciplinas → 200 com lista vazia")
    void listarVazio() throws Exception {
        when(service.listarDisciplinas()).thenReturn(List.of());

        mockMvc.perform(get("/api/disciplinas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}