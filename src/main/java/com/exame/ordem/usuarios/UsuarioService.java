package com.exame.ordem.usuarios;

import com.exame.ordem.usuarios.dto.AtualizarPerfilRequest;
import com.exame.ordem.usuarios.dto.CadastroUsuarioRequest;
import com.exame.ordem.usuarios.dto.UsuarioResumo;

public interface UsuarioService {

    UsuarioResumo cadastrar(CadastroUsuarioRequest request);

    /** Busca por ID — usado pelos endpoints autenticados (JWT). */
    UsuarioResumo buscarPorId(Long usuarioId);

    /** Atualiza o perfil do usuário autenticado. */
    UsuarioResumo atualizarPerfil(Long usuarioId, AtualizarPerfilRequest request);

    /** Exclui a conta do usuário autenticado (LGPD). */
    void excluirConta(Long usuarioId);
}