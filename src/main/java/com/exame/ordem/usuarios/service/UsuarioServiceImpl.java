package com.exame.ordem.usuarios.service;

import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import com.exame.ordem.usuarios.UsuarioExcluido;
import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.AtualizarPerfilRequest;
import com.exame.ordem.usuarios.dto.CadastroUsuarioRequest;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import com.exame.ordem.usuarios.internal.OrigemTrial;
import com.exame.ordem.usuarios.internal.ProvedorAuth;
import com.exame.ordem.usuarios.internal.Usuario;
import com.exame.ordem.usuarios.internal.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int trialDias;

    UsuarioServiceImpl(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher events,
                       Clock clock,
                       @Value("${app.trial.dias:7}") int trialDias) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
        this.trialDias = trialDias;
    }

    @Override
    @Transactional
    public UsuarioResumo cadastrar(CadastroUsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new ConflitoDeEstadoException("E-mail já cadastrado.");
        }
        String hash = passwordEncoder.encode(request.senha());
        Usuario usuario = Usuario.criarComSenha(request.email(), hash, request.nome());

        // Trial reverso (RF12.8, D11, D32)
        usuario.iniciarTrial(Instant.now(clock), trialDias, OrigemTrial.AUTOMATICO);

        usuario = usuarioRepository.save(usuario);
        return toResumo(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResumo buscarPorId(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        return toResumo(usuario);
    }

    @Override
    @Transactional
    public UsuarioResumo atualizarPerfil(Long usuarioId, AtualizarPerfilRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        if (request.novoNome() != null && !request.novoNome().isBlank()) {
            usuario.alterarNome(request.novoNome());
        }

        if (request.dataProvaAlvo() != null) {
            usuario.alterarDataProvaAlvo(request.dataProvaAlvo());
        }

        if (request.novaSenha() != null && !request.novaSenha().isBlank()) {
            if (usuario.getProvedorAuth() != ProvedorAuth.LOCAL) {
                throw new ConflitoDeEstadoException(
                        "Esta conta usa login via " + usuario.getProvedorAuth() +
                                ". Troca de senha não se aplica a contas de provedor externo.");
            }
            usuario.alterarSenhaHash(passwordEncoder.encode(request.novaSenha()));
        }

        usuario = usuarioRepository.save(usuario);
        return toResumo(usuario);
    }

    @Override
    @Transactional
    public void excluirConta(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        // R18/ADR-010: publica antes do DELETE, dentro da mesma transação.
        // O Modulith persiste o evento em event_publication e reentrega se falhar.
        events.publishEvent(new UsuarioExcluido(usuarioId));

        usuarioRepository.delete(usuario);
    }

    private UsuarioResumo toResumo(Usuario usuario) {
        return new UsuarioResumo(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNome(),
                usuario.getDataProvaAlvo(),
                usuario.getCriadoEm());
    }
}