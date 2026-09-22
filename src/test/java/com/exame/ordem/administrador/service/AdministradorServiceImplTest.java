package com.exame.ordem.administradores.service;

import com.exame.ordem.administradores.dto.CadastroAdministradorRequest;
import com.exame.ordem.administradores.internal.Administrador;
import com.exame.ordem.administradores.internal.AdministradorRepository;
import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdministradorServiceImplTest {

    @Mock AdministradorRepository repository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks AdministradorServiceImpl service;

    // ================================================================
    //  cadastrar
    // ================================================================

    @Test
    @DisplayName("cadastrar → persiste admin com senha hasheada")
    void cadastrar_sucesso() {
        when(repository.existsByEmail("admin@teste.com")).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("$2a$10$hash");

        service.cadastrar(new CadastroAdministradorRequest("admin@teste.com", "senha1234", "Admin"));

        verify(passwordEncoder).encode("senha1234");
        verify(repository).save(any(Administrador.class));
    }

    @Test
    @DisplayName("cadastrar → email duplicado → ConflitoDeEstadoException")
    void cadastrar_emailDuplicado() {
        when(repository.existsByEmail("dup@teste.com")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(
                new CadastroAdministradorRequest("dup@teste.com", "senha1234", "Admin")))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("já cadastrado");

        verify(repository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}