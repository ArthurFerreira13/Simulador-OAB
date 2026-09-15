package com.exame.ordem.usuarios.service;

import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.*;
import com.exame.ordem.usuarios.internal.ProvedorAuth;
import com.exame.ordem.usuarios.internal.Usuario;
import com.exame.ordem.usuarios.internal.UsuarioRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    UsuarioServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UsuarioResumo cadastrar(CadastroUsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new IllegalStateException("E-mail já cadastrado.");
        }
        String hash = passwordEncoder.encode(request.senha());
        Usuario usuario = Usuario.criarComSenha(request.email(), hash, request.nome());
        usuario = usuarioRepository.save(usuario);
        return new UsuarioResumo(usuario.getId(), usuario.getEmail(), usuario.getNome(), usuario.getDataProvaAlvo(), usuario.getCriadoEm());
    }

    @Override
    public void atualizarPerfil(AtualizarPerfilRequest request) {
        Usuario usuario = autenticarOuFalhar(request.email(), request.senhaAtual());

        if (request.novoNome() != null && !request.novoNome().isBlank()) {
            usuario.alterarNome(request.novoNome());
        }
        if (request.novaSenha() != null && !request.novaSenha().isBlank()) {
            usuario.alterarSenhaHash(passwordEncoder.encode(request.novaSenha()));
        }

        usuarioRepository.save(usuario);
    }

    @Override
    public void excluirConta(ExcluirContaRequest request) {
        Usuario usuario = autenticarOuFalhar(request.email(), request.senha());
        usuarioRepository.delete(usuario);
    }

    private Usuario autenticarOuFalhar(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new SecurityException("Credenciais inválidas."));

        if (usuario.getProvedorAuth() != ProvedorAuth.LOCAL) {
            throw new IllegalStateException(
                    "Esta conta usa login via " + usuario.getProvedorAuth() +
                            ". Esta operação não se aplica a contas de provedor externo.");
        }

        if (!passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new SecurityException("Credenciais inválidas.");
        }
        return usuario;
    }
    @Override
    public UsuarioResumo consultarPerfil(String email, String senha) {
        Usuario usuario = autenticarOuFalhar(email, senha);
        return new UsuarioResumo(usuario.getId(), usuario.getEmail(), usuario.getNome(), usuario.getDataProvaAlvo(), usuario.getCriadoEm());
    }
}