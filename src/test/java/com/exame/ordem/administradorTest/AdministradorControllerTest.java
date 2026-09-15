package com.exame.ordem.administradorTest;



import com.exame.ordem.administradores.AdministradorService;
import com.exame.ordem.administradores.controller.AdministradorController;
import com.exame.ordem.administradores.dto.CadastroAdministradorRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;   // ← MUDANÇA AQUI

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdministradorController.class)
@ActiveProfiles("dev")
class AdministradorControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean
    AdministradorService administradorService;

    @Test
    @DisplayName("POST /api/administradores → 201 quando cadastro é válido")
    void deveCadastrarComSucesso() throws Exception {
        var request = new CadastroAdministradorRequest("admin@teste.com", "senha1234", "Admin");

        doNothing().when(administradorService).cadastrar(any());

        mockMvc.perform(post("/api/administradores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        verify(administradorService).cadastrar(any());
    }

    @Test
    @DisplayName("POST /api/administradores → 409 quando e-mail já existe")
    void deveRetornar409QuandoEmailDuplicado() throws Exception {
        var request = new CadastroAdministradorRequest("dup@teste.com", "senha1234", "Admin");

        doThrow(new IllegalStateException("E-mail já cadastrado."))
                .when(administradorService).cadastrar(any());

        mockMvc.perform(post("/api/administradores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/administradores → 400 quando e-mail é inválido")
    void deveRetornar400QuandoEmailInvalido() throws Exception {
        var request = new CadastroAdministradorRequest("nao-eh-email", "senha1234", "Admin");

        mockMvc.perform(post("/api/administradores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(administradorService);
    }

    @Test
    @DisplayName("POST /api/administradores → 400 quando senha tem menos de 8 chars")
    void deveRetornar400QuandoSenhaCurta() throws Exception {
        var request = new CadastroAdministradorRequest("admin@teste.com", "123", "Admin");

        mockMvc.perform(post("/api/administradores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(administradorService);
    }

    @Test
    @DisplayName("POST /api/administradores → 400 quando nome em branco")
    void deveRetornar400QuandoNomeEmBranco() throws Exception {
        var request = new CadastroAdministradorRequest("admin@teste.com", "senha1234", "  ");

        mockMvc.perform(post("/api/administradores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(administradorService);
    }
}