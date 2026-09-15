package com.exame.ordem.administradorTest;


import com.exame.ordem.administradores.dto.CadastroAdministradorRequest;
import com.exame.ordem.administradores.internal.Administrador;
import com.exame.ordem.administradores.internal.AdministradorRepository;
import com.exame.ordem.administradores.service.AdministradorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdministradorServiceImplTest {

    @Mock
    private AdministradorRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdministradorServiceImpl service;

    @BeforeEach
    void setup() {
        service = new AdministradorServiceImpl(repository, passwordEncoder);
    }

    // ------------------------------------------------------------------
    // Caso feliz
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Deve cadastrar administrador com sucesso, salvando senha como hash")
    void deveCadastrarComSucesso() {
        var request = new CadastroAdministradorRequest("admin@teste.com", "senha1234", "Admin Teste");

        when(repository.existsByEmail("admin@teste.com")).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("$2a$10$hashFake");

        assertThatCode(() -> service.cadastrar(request)).doesNotThrowAnyException();

        ArgumentCaptor<Administrador> captor = ArgumentCaptor.forClass(Administrador.class);
        verify(repository).save(captor.capture());

        Administrador salvo = captor.getValue();
        assertThat(salvo.getEmail()).isEqualTo("admin@teste.com");
        assertThat(salvo.getNome()).isEqualTo("Admin Teste");
        // não conseguimos acessar senhaHash diretamente (não tem getter),
        // mas validamos que o passwordEncoder foi chamado corretamente
        verify(passwordEncoder).encode("senha1234");
    }

    // ------------------------------------------------------------------
    // E-mail duplicado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Deve lançar IllegalStateException quando e-mail já existe")
    void deveLancarExcecaoQuandoEmailJaExiste() {
        var request = new CadastroAdministradorRequest("admin@teste.com", "senha1234", "Admin");

        when(repository.existsByEmail("admin@teste.com")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("E-mail já cadastrado");

        verify(repository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    // ------------------------------------------------------------------
    // Nunca salva quando e-mail é duplicado (garantia extra)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Não deve chamar save() se o e-mail já existe")
    void naoDeveSalvarSeEmailDuplicado() {
        var request = new CadastroAdministradorRequest("dup@teste.com", "senha1234", "X");

        when(repository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(request))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, times(1)).existsByEmail("dup@teste.com");
        verifyNoMoreInteractions(repository);
    }
}