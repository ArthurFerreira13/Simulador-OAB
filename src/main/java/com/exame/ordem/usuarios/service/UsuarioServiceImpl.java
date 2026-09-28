package com.exame.ordem.usuarios.service;

import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.CredenciaisInvalidasException;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.AtualizarPerfilRequest;
import com.exame.ordem.usuarios.dto.CadastroUsuarioRequest;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import com.exame.ordem.usuarios.internal.ProvedorAuth;
import com.exame.ordem.usuarios.internal.Usuario;
import com.exame.ordem.usuarios.internal.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    UsuarioServiceImpl(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UsuarioResumo cadastrar(CadastroUsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new ConflitoDeEstadoException("E-mail já cadastrado.");
        }
        String hash = passwordEncoder.encode(request.senha());
        Usuario usuario = Usuario.criarComSenha(request.email(), hash, request.nome());
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
        usuarioRepository.delete(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResumo consultarPerfil(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new CredenciaisInvalidasException("Credenciais inválidas."));

        if (usuario.getProvedorAuth() != ProvedorAuth.LOCAL) {
            throw new CredenciaisInvalidasException("Credenciais inválidas.");
        }

        if (usuario.getSenhaHash() == null
                || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException("Credenciais inválidas.");
        }

        return toResumo(usuario);
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