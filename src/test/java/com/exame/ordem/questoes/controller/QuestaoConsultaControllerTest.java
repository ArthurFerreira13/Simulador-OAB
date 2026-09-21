package com.exame.ordem.questoes.controller;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.AlternativaResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuestaoConsultaController.class)
class QuestaoConsultaControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean QuestoesQueryService service;

    @Test
    @DisplayName("GET /api/questoes/{id} → 200 com questão")
    void buscarPorId() throws Exception {
        QuestaoResumo questao = new QuestaoResumo(
                100, 5, 1, "Enunciado...", false,
                "Ética e Estatuto da Advocacia",
                List.of(
                        new AlternativaResumo(1001L, "A", "Texto A"),
                        new AlternativaResumo(1002L, "B", "Texto B"),
                        new AlternativaResumo(1003L, "C", "Texto C"),
                        new AlternativaResumo(1004L, "D", "Texto D")
                )
        );
        when(service.buscarQuestaoPorId(100)).thenReturn(questao);

        mockMvc.perform(get("/api/questoes/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.provaId").value(5))
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.anulada").value(false))
                .andExpect(jsonPath("$.disciplina").value("Ética e Estatuto da Advocacia"))
                .andExpect(jsonPath("$.alternativas", hasSize(4)))
                .andExpect(jsonPath("$.alternativas[0].letra").value("A"));
    }

    @Test
    @DisplayName("GET /api/questoes/{id} → 404 se não existe")
    void buscarPorIdInexistente() throws Exception {
        when(service.buscarQuestaoPorId(999))
                .thenThrow(new IllegalArgumentException("não encontrada"));

        mockMvc.perform(get("/api/questoes/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/questoes/{id} → não expõe gabarito")
    void naoExpoeGabarito() throws Exception {
        QuestaoResumo questao = new QuestaoResumo(
                100, 5, 1, "Enunciado...", false,
                "Ética",
                List.of(new AlternativaResumo(1001L, "A", "Texto A"))
        );
        when(service.buscarQuestaoPorId(100)).thenReturn(questao);

        mockMvc.perform(get("/api/questoes/100"))
                .andExpect(status().isOk())
                // garante que o serializador não inclui ehCorreta nem comentario
                .andExpect(jsonPath("$.alternativas[0].ehCorreta").doesNotExist())
                .andExpect(jsonPath("$.alternativas[0].comentario").doesNotExist());
    }
}