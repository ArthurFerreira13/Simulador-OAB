package com.exame.ordem.usuarios.service;

import com.exame.ordem.system.erro.excecoes.ConflitoDeEstadoException;
import com.exame.ordem.system.erro.excecoes.CredenciaisInvalidasException;
import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.*;
import com.exame.ordem.usuarios.internal.ProvedorAuth;
import com.exame.ordem.usuarios.internal.Usuario;
import com.exame.ordem.usuarios.internal.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    UsuarioServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UsuarioResumo cadastrar(CadastroUsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new ConflitoDeEstadoException("E-mail já cadastrado.");
        }
        String hash = passwordEncoder.encode(request.senha());
        Usuario usuario = Usuario.criarComSenha(request.email(), hash, request.nome());
        usuario = usuarioRepository.save(usuario);
        return new UsuarioResumo(usuario.getId(), usuario.getEmail(), usuario.getNome(),
                usuario.getDataProvaAlvo(), usuario.getCriadoEm());
    }

    @Override
    public UsuarioResumo atualizarPerfil(AtualizarPerfilRequest request) {
        Usuario usuario = autenticarOuFalhar(request.email(), request.senhaAtual());

        if (request.novoNome() != null && !request.novoNome().isBlank()) {
            usuario.alterarNome(request.novoNome());
        }
        if (request.novaSenha() != null && !request.novaSenha().isBlank()) {
            usuario.alterarSenhaHash(passwordEncoder.encode(request.novaSenha()));
        }

        usuario = usuarioRepository.save(usuario);

        return new UsuarioResumo(usuario.getId(), usuario.getEmail(), usuario.getNome(),
                usuario.getDataProvaAlvo(), usuario.getCriadoEm());
    }

    @Override
    public void excluirConta(ExcluirContaRequest request) {
        Usuario usuario = autenticarOuFalhar(request.email(), request.senha());
        usuarioRepository.delete(usuario);
    }

    @Override
    public UsuarioResumo consultarPerfil(String email, String senha) {
        Usuario usuario = autenticarOuFalhar(email, senha);
        return new UsuarioResumo(usuario.getId(), usuario.getEmail(), usuario.getNome(),
                usuario.getDataProvaAlvo(), usuario.getCriadoEm());
    }

    private Usuario autenticarOuFalhar(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new CredenciaisInvalidasException("Credenciais inválidas."));

        if (usuario.getProvedorAuth() != ProvedorAuth.LOCAL) {
            throw new RegraDeNegocioException(
                    "Esta conta usa login via " + usuario.getProvedorAuth() +
                            ". Esta operação não se aplica a contas de provedor externo.");
        }

        if (!passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException("Credenciais inválidas.");
        }
        return usuario;
    }
}