package com.exame.ordem.usuarios.service;

import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.CredenciaisInvalidasException;
import com.exame.ordem.usuarios.dto.*;
import com.exame.ordem.usuarios.internal.ProvedorAuth;
import com.exame.ordem.usuarios.internal.Usuario;
import com.exame.ordem.usuarios.internal.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks
    UsuarioServiceImpl service;

    // ================================================================
    //  cadastrar
    // ================================================================

    @Test
    @DisplayName("cadastrar → cria usuário e retorna UsuarioResumo")
    void cadastrar_sucesso() {
        when(usuarioRepository.existsByEmail("novo@teste.com")).thenReturn(false);
        when(passwordEncoder.encode("senha12345")).thenReturn("$2a$10$hash");

        Usuario salvo = usuarioLocal(1L, "novo@teste.com", "Novo", "$2a$10$hash");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(salvo);

        UsuarioResumo resumo = service.cadastrar(
                new CadastroUsuarioRequest("novo@teste.com", "senha12345", "Novo"));

        assertThat(resumo.id()).isEqualTo(1L);
        assertThat(resumo.email()).isEqualTo("novo@teste.com");
        assertThat(resumo.nome()).isEqualTo("Novo");
        verify(passwordEncoder).encode("senha12345");
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    @DisplayName("cadastrar → email já existe → ConflitoDeEstadoException")
    void cadastrar_emailDuplicado() {
        when(usuarioRepository.existsByEmail("existente@teste.com")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(
                new CadastroUsuarioRequest("existente@teste.com", "senha12345", "X")))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("já cadastrado");

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    // ================================================================
    //  consultarPerfil
    // ================================================================

    @Test
    @DisplayName("consultarPerfil → retorna UsuarioResumo")
    void consultarPerfil_sucesso() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findByEmail("arthur@teste.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha12345", "$2a$10$hash")).thenReturn(true);

        UsuarioResumo resumo = service.consultarPerfil("arthur@teste.com", "senha12345");

        assertThat(resumo.email()).isEqualTo("arthur@teste.com");
        assertThat(resumo.nome()).isEqualTo("Arthur");
    }

    @Test
    @DisplayName("consultarPerfil → usuário não existe → CredenciaisInvalidasException")
    void consultarPerfil_usuarioInexistente() {
        when(usuarioRepository.findByEmail("naoexiste@teste.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarPerfil("naoexiste@teste.com", "senha12345"))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessageContaining("Credenciais inválidas");
    }

    @Test
    @DisplayName("consultarPerfil → senha errada → CredenciaisInvalidasException")
    void consultarPerfil_senhaErrada() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findByEmail("arthur@teste.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senhaERRADA", "$2a$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.consultarPerfil("arthur@teste.com", "senhaERRADA"))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessageContaining("Credenciais inválidas");
    }

    // ================================================================
    //  atualizarPerfil
    // ================================================================

    @Test
    @DisplayName("atualizarPerfil → altera só nome")
    void atualizarPerfil_alteraNome() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findByEmail("arthur@teste.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha12345", "$2a$10$hash")).thenReturn(true);
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        UsuarioResumo resumo = service.atualizarPerfil(new AtualizarPerfilRequest(
                "arthur@teste.com", "senha12345", "Arthur Silva", null));

        assertThat(resumo.nome()).isEqualTo("Arthur Silva");
        verify(passwordEncoder, never()).encode(anyString());  // não trocou a senha
    }

    @Test
    @DisplayName("atualizarPerfil → altera só senha")
    void atualizarPerfil_alteraSenha() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findByEmail("arthur@teste.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha12345", "$2a$10$hash")).thenReturn(true);
        when(passwordEncoder.encode("novasenha456")).thenReturn("$2a$10$novohash");
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        service.atualizarPerfil(new AtualizarPerfilRequest(
                "arthur@teste.com", "senha12345", null, "novasenha456"));

        assertThat(usuario.getSenhaHash()).isEqualTo("$2a$10$novohash");
        assertThat(usuario.getNome()).isEqualTo("Arthur");   // nome não mudou
    }

    @Test
    @DisplayName("atualizarPerfil → altera nome e senha")
    void atualizarPerfil_alteraAmbos() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findByEmail("arthur@teste.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha12345", "$2a$10$hash")).thenReturn(true);
        when(passwordEncoder.encode("novasenha456")).thenReturn("$2a$10$novohash");
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        UsuarioResumo resumo = service.atualizarPerfil(new AtualizarPerfilRequest(
                "arthur@teste.com", "senha12345", "Arthur Silva", "novasenha456"));

        assertThat(resumo.nome()).isEqualTo("Arthur Silva");
        assertThat(usuario.getSenhaHash()).isEqualTo("$2a$10$novohash");
    }

    @Test
    @DisplayName("atualizarPerfil → senha atual errada → CredenciaisInvalidasException")
    void atualizarPerfil_senhaAtualErrada() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findByEmail("arthur@teste.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "$2a$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.atualizarPerfil(new AtualizarPerfilRequest(
                "arthur@teste.com", "errada", "Hacker", "novasenha456")))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(usuarioRepository, never()).save(any());  // não persistiu
    }

    @Test
    @DisplayName("atualizarPerfil → nome em branco é ignorado")
    void atualizarPerfil_nomeEmBranco() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findByEmail("arthur@teste.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha12345", "$2a$10$hash")).thenReturn(true);
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        service.atualizarPerfil(new AtualizarPerfilRequest(
                "arthur@teste.com", "senha12345", "  ", null));

        assertThat(usuario.getNome()).isEqualTo("Arthur");  // não mudou
    }

    // ================================================================
    //  excluirConta
    // ================================================================

    @Test
    @DisplayName("excluirConta → deleta usuário autenticado")
    void excluirConta_sucesso() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findByEmail("arthur@teste.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha12345", "$2a$10$hash")).thenReturn(true);

        service.excluirConta(new ExcluirContaRequest("arthur@teste.com", "senha12345"));

        verify(usuarioRepository).delete(usuario);
    }

    @Test
    @DisplayName("excluirConta → senha errada → CredenciaisInvalidasException")
    void excluirConta_senhaErrada() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findByEmail("arthur@teste.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "$2a$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.excluirConta(
                new ExcluirContaRequest("arthur@teste.com", "errada")))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(usuarioRepository, never()).delete(any());
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private Usuario usuarioLocal(Long id, String email, String nome, String senhaHash) {
        Usuario u = Usuario.criarComSenha(email, senhaHash, nome);
        ReflectionTestUtils.setField(u, "id", id);
        ReflectionTestUtils.setField(u, "provedorAuth", ProvedorAuth.LOCAL);
        return u;
    }
}