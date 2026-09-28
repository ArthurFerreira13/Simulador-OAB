package com.exame.ordem.usuarios.controller;

import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsuarioController.class)
class UsuarioControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UsuarioService usuarioService;

    // ================================================================
    //  POST /api/usuarios  (público — sem token)
    // ================================================================

    @Test
    @DisplayName("POST /api/usuarios → 201 com UsuarioResumo")
    void cadastrar() throws Exception {
        UsuarioResumo criado = usuarioResumo(1L, "novo@teste.com", "Novo");
        when(usuarioService.cadastrar(any(CadastroUsuarioRequest.class))).thenReturn(criado);

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "novo@teste.com",
                                  "senha": "senha12345",
                                  "nome": "Novo"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("novo@teste.com"))
                .andExpect(jsonPath("$.nome").value("Novo"));
    }

    @Test
    @DisplayName("POST /api/usuarios → 409 quando email duplicado")
    void cadastrar_emailDuplicado() throws Exception {
        when(usuarioService.cadastrar(any(CadastroUsuarioRequest.class)))
                .thenThrow(new ConflitoDeEstadoException("E-mail já cadastrado."));

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "existente@teste.com",
                                  "senha": "senha12345",
                                  "nome": "X"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLITO_DE_ESTADO"))
                .andExpect(jsonPath("$.mensagem").value("E-mail já cadastrado."));
    }

    @Test
    @DisplayName("POST /api/usuarios → 400 quando body inválido (@Valid)")
    void cadastrar_bodyInvalido() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "nao-eh-email",
                                  "senha": "123",
                                  "nome": "  "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));

        verify(usuarioService, org.mockito.Mockito.never()).cadastrar(any());
    }

    // ================================================================
    //  GET /api/usuarios/perfil  (autenticado)
    // ================================================================

    @Test
    @WithMockUser(username = "1")
    @DisplayName("GET /api/usuarios/perfil → 200 com UsuarioResumo")
    void consultarPerfil() throws Exception {
        UsuarioResumo perfil = usuarioResumo(1L, "arthur@teste.com", "Arthur");
        when(usuarioService.buscarPorId(1L)).thenReturn(perfil);

        mockMvc.perform(get("/api/usuarios/perfil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("arthur@teste.com"))
                .andExpect(jsonPath("$.nome").value("Arthur"));
    }

    // ================================================================
    //  PUT /api/usuarios/perfil  (autenticado)
    // ================================================================

    @Test
    @WithMockUser(username = "1")
    @DisplayName("PUT /api/usuarios/perfil → 200 com UsuarioResumo atualizado")
    void atualizarPerfil() throws Exception {
        UsuarioResumo atualizado = usuarioResumo(1L, "arthur@teste.com", "Arthur Silva");
        when(usuarioService.atualizarPerfil(eq(1L), any(AtualizarPerfilRequest.class)))
                .thenReturn(atualizado);

        mockMvc.perform(put("/api/usuarios/perfil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "novoNome": "Arthur Silva"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Arthur Silva"));
    }

    @Test
    @WithMockUser(username = "1")
    @DisplayName("PUT /api/usuarios/perfil → 400 quando novaSenha curta")
    void atualizarPerfil_senhaCurta() throws Exception {
        mockMvc.perform(put("/api/usuarios/perfil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "novaSenha": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));

        verify(usuarioService, org.mockito.Mockito.never()).atualizarPerfil(any(), any());
    }

    // ================================================================
    //  DELETE /api/usuarios  (autenticado)
    // ================================================================

    @Test
    @WithMockUser(username = "1")
    @DisplayName("DELETE /api/usuarios → 204 quando exclui")
    void excluirConta() throws Exception {
        mockMvc.perform(delete("/api/usuarios"))
                .andExpect(status().isNoContent());

        verify(usuarioService).excluirConta(1L);
    }

    // ================================================================
    //  Helper
    // ================================================================

    private UsuarioResumo usuarioResumo(Long id, String email, String nome) {
        return new UsuarioResumo(id, email, nome, null, LocalDateTime.now(ZoneOffset.UTC));
    }
}