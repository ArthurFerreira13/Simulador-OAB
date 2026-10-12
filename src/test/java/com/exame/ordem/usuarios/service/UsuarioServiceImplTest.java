package com.exame.ordem.usuarios.service;

import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import com.exame.ordem.usuarios.dto.AtualizarPerfilRequest;
import com.exame.ordem.usuarios.dto.CadastroUsuarioRequest;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
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

import java.time.LocalDate;
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
    //  buscarPorId
    // ================================================================

    @Test
    @DisplayName("buscarPorId → retorna UsuarioResumo")
    void buscarPorId_sucesso() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        UsuarioResumo resumo = service.buscarPorId(1L);

        assertThat(resumo.id()).isEqualTo(1L);
        assertThat(resumo.email()).isEqualTo("arthur@teste.com");
        assertThat(resumo.nome()).isEqualTo("Arthur");
    }

    @Test
    @DisplayName("buscarPorId → não existe → RecursoNaoEncontradoException")
    void buscarPorId_inexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("não encontrado");
    }

    // ================================================================
    //  atualizarPerfil
    //  Assinatura atual: (Long id, AtualizarPerfilRequest)
    //  AtualizarPerfilRequest: (String novoNome, LocalDate dataProvaAlvo, String novaSenha)
    // ================================================================

    @Test
    @DisplayName("atualizarPerfil → altera só nome")
    void atualizarPerfil_alteraNome() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        UsuarioResumo resumo = service.atualizarPerfil(1L,
                new AtualizarPerfilRequest("Arthur Silva", null, null));

        assertThat(resumo.nome()).isEqualTo("Arthur Silva");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("atualizarPerfil → altera só data da prova")
    void atualizarPerfil_alteraDataProva() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        LocalDate novaData = LocalDate.now().plusMonths(6);

        UsuarioResumo resumo = service.atualizarPerfil(1L,
                new AtualizarPerfilRequest(null, novaData, null));

        assertThat(resumo.dataProvaAlvo()).isEqualTo(novaData);
        assertThat(usuario.getNome()).isEqualTo("Arthur");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("atualizarPerfil → altera só senha")
    void atualizarPerfil_alteraSenha() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("novasenha456")).thenReturn("$2a$10$novohash");
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        service.atualizarPerfil(1L,
                new AtualizarPerfilRequest(null, null, "novasenha456"));

        assertThat(usuario.getSenhaHash()).isEqualTo("$2a$10$novohash");
        assertThat(usuario.getNome()).isEqualTo("Arthur");
    }

    @Test
    @DisplayName("atualizarPerfil → altera nome e senha")
    void atualizarPerfil_alteraAmbos() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("novasenha456")).thenReturn("$2a$10$novohash");
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        UsuarioResumo resumo = service.atualizarPerfil(1L,
                new AtualizarPerfilRequest("Arthur Silva", null, "novasenha456"));

        assertThat(resumo.nome()).isEqualTo("Arthur Silva");
        assertThat(usuario.getSenhaHash()).isEqualTo("$2a$10$novohash");
    }

    @Test
    @DisplayName("atualizarPerfil → nome em branco é ignorado")
    void atualizarPerfil_nomeEmBranco() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        service.atualizarPerfil(1L,
                new AtualizarPerfilRequest("  ", null, null));

        assertThat(usuario.getNome()).isEqualTo("Arthur");
    }

    @Test
    @DisplayName("atualizarPerfil → usuário não existe → RecursoNaoEncontradoException")
    void atualizarPerfil_usuarioInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarPerfil(99L,
                new AtualizarPerfilRequest("X", null, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("atualizarPerfil → conta não-LOCAL + novaSenha → ConflitoDeEstadoException")
    void atualizarPerfil_contaExternaTentandoTrocarSenha() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        ReflectionTestUtils.setField(usuario, "provedorAuth", ProvedorAuth.GOOGLE);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.atualizarPerfil(1L,
                new AtualizarPerfilRequest(null, null, "novasenha456")))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("provedor externo");

        verify(usuarioRepository, never()).save(any());
    }

    // ================================================================
    //  excluirConta
    // ================================================================

    @Test
    @DisplayName("excluirConta → deleta usuário autenticado")
    void excluirConta_sucesso() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        service.excluirConta(1L);

        verify(usuarioRepository).delete(usuario);
    }

    @Test
    @DisplayName("excluirConta → conta não-LOCAL também pode excluir (LGPD)")
    void excluirConta_contaExterna() {
        Usuario usuario = usuarioLocal(1L, "arthur@teste.com", "Arthur", "$2a$10$hash");
        ReflectionTestUtils.setField(usuario, "provedorAuth", ProvedorAuth.GOOGLE);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        service.excluirConta(1L);

        verify(usuarioRepository).delete(usuario);
    }

    @Test
    @DisplayName("excluirConta → usuário não existe → RecursoNaoEncontradoException")
    void excluirConta_inexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluirConta(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);

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