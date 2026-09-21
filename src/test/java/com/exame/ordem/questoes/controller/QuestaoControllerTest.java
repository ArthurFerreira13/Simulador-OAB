package com.exame.ordem.questoes.controller;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.AlternativaComGabarito;
import com.exame.ordem.questoes.dto.AlternativaResumo;
import com.exame.ordem.questoes.dto.ProvaResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.questoes.dto.RespostaDetalhadaDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuestaoController.class)
class QuestaoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QuestoesQueryService service;

    // ================================================================
    //  GET /api/exames
    // ================================================================

    @Test
    @DisplayName("GET /api/exames → 200 com provas (id, titulo, ano)")
    void listarProvas() throws Exception {
        when(service.listarProvas()).thenReturn(List.of(
                new ProvaResumo(5, "43º EXAME DE ORDEM UNIFICADO - TIPO 1", 2025),
                new ProvaResumo(7, "44º EXAME DE ORDEM UNIFICADO - TIPO 1", 2025)
        ));

        mockMvc.perform(get("/api/exames"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].titulo").value("43º EXAME DE ORDEM UNIFICADO - TIPO 1"))
                .andExpect(jsonPath("$[0].ano").value(2025))
                // DTO não tem tipoCor — garante que não vazou
                .andExpect(jsonPath("$[0].tipoCor").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/exames → 200 com lista vazia")
    void listarProvasVazio() throws Exception {
        when(service.listarProvas()).thenReturn(List.of());

        mockMvc.perform(get("/api/exames"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ================================================================
    //  GET /api/exames/{numero}/questoes
    // ================================================================

    @Test
    @DisplayName("GET /api/exames/{numero}/questoes → 200 com questões da prova")
    void buscarQuestoesDoExame() throws Exception {
        when(service.buscarQuestoesPorProva(43)).thenReturn(List.of(
                questao(100, 5, 1),
                questao(101, 5, 2)
        ));

        mockMvc.perform(get("/api/exames/43/questoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[1].numero").value(2));
    }

    @Test
    @DisplayName("GET /api/exames/{numero}/questoes → 404 se prova não existe")
    void buscarQuestoesDoExameInexistente() throws Exception {
        when(service.buscarQuestoesPorProva(99))
                .thenThrow(new IllegalArgumentException("Prova não encontrada"));

        mockMvc.perform(get("/api/exames/99/questoes"))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    //  GET /api/exames/questoes?filtros
    // ================================================================

    @Test
    @DisplayName("GET /api/exames/questoes → sem filtros passa null/null")
    void buscarQuestoesSemFiltro() throws Exception {
        when(service.buscarPorDisciplinaEAno(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/exames/questoes"))
                .andExpect(status().isOk());

        verify(service).buscarPorDisciplinaEAno(null, null);
    }

    @Test
    @DisplayName("GET /api/exames/questoes?ano=2025 → repassa só ano")
    void buscarQuestoesFiltroAno() throws Exception {
        when(service.buscarPorDisciplinaEAno(null, 2025)).thenReturn(List.of());

        mockMvc.perform(get("/api/exames/questoes").param("ano", "2025"))
                .andExpect(status().isOk());

        verify(service).buscarPorDisciplinaEAno(null, 2025);
    }

    @Test
    @DisplayName("GET /api/exames/questoes?disciplinaId=1 → repassa só disciplina")
    void buscarQuestoesFiltroDisciplina() throws Exception {
        when(service.buscarPorDisciplinaEAno(1L, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/exames/questoes").param("disciplinaId", "1"))
                .andExpect(status().isOk());

        verify(service).buscarPorDisciplinaEAno(1L, null);
    }

    @Test
    @DisplayName("GET /api/exames/questoes?ano=2025&disciplinaId=1 → repassa ambos")
    void buscarQuestoesFiltroCombinado() throws Exception {
        when(service.buscarPorDisciplinaEAno(1L, 2025)).thenReturn(List.of());

        mockMvc.perform(get("/api/exames/questoes")
                        .param("ano", "2025")
                        .param("disciplinaId", "1"))
                .andExpect(status().isOk());

        verify(service).buscarPorDisciplinaEAno(1L, 2025);
    }

    // ================================================================
    //  POST /api/exames/questoes/{id}/responder
    // ================================================================

    @Test
    @DisplayName("POST responder → 200 com resposta detalhada (correta)")
    void responderCorreta() throws Exception {
        RespostaDetalhadaDTO resposta = new RespostaDetalhadaDTO(
                100, 1003L, true,
                List.of(
                        new AlternativaComGabarito(1001L, "A", "Texto A", "A está incorreta.", false),
                        new AlternativaComGabarito(1003L, "C", "Texto C", "C está correta.", true)
                )
        );
        when(service.verificarResposta(100, 1003L)).thenReturn(resposta);

        mockMvc.perform(post("/api/exames/questoes/100/responder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alternativaId\": 1003}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questaoId").value(100))
                .andExpect(jsonPath("$.alternativaEscolhidaId").value(1003))
                .andExpect(jsonPath("$.correta").value(true))
                .andExpect(jsonPath("$.alternativas", hasSize(2)))
                // agora SIM expõe o gabarito (é a resposta detalhada)
                .andExpect(jsonPath("$.alternativas[1].ehCorreta").value(true))
                .andExpect(jsonPath("$.alternativas[1].comentario").value("C está correta."));
    }

    @Test
    @DisplayName("POST responder → 200 com correta=false")
    void responderIncorreta() throws Exception {
        RespostaDetalhadaDTO resposta = new RespostaDetalhadaDTO(
                100, 1001L, false,
                List.of(new AlternativaComGabarito(1001L, "A", "Texto A", "A está incorreta.", false))
        );
        when(service.verificarResposta(100, 1001L)).thenReturn(resposta);

        mockMvc.perform(post("/api/exames/questoes/100/responder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alternativaId\": 1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correta").value(false));
    }

    @Test
    @DisplayName("POST responder → 404 se questão não existe")
    void responderQuestaoInexistente() throws Exception {
        when(service.verificarResposta(eq(999), any()))
                .thenThrow(new IllegalArgumentException("Questão não encontrada"));

        mockMvc.perform(post("/api/exames/questoes/999/responder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alternativaId\": 1003}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST responder → 404 se alternativa de outra questão")
    void responderAlternativaDeOutraQuestao() throws Exception {
        when(service.verificarResposta(100, 9999L))
                .thenThrow(new IllegalArgumentException("Alternativa 9999 não encontrada para a questão 100"));

        mockMvc.perform(post("/api/exames/questoes/100/responder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alternativaId\": 9999}"))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    //  Helper
    // ================================================================

    private QuestaoResumo questao(int id, int provaId, int numero) {
        return new QuestaoResumo(
                id, provaId, numero,
                "Enunciado da questão " + numero,
                false,
                "Ética",
                List.of(new AlternativaResumo(1000L + numero, "A", "Texto A"))
        );
    }
}