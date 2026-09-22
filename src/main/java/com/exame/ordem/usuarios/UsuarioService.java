// com/exame/ordem/usuarios/UsuarioService.java
package com.exame.ordem.usuarios;

import com.exame.ordem.usuarios.dto.AtualizarPerfilRequest;
import com.exame.ordem.usuarios.dto.CadastroUsuarioRequest;
import com.exame.ordem.usuarios.dto.ExcluirContaRequest;
import com.exame.ordem.usuarios.dto.UsuarioResumo;

public interface UsuarioService {
    UsuarioResumo cadastrar(CadastroUsuarioRequest request);
    UsuarioResumo atualizarPerfil(AtualizarPerfilRequest request);
    void excluirConta(ExcluirContaRequest request);
    UsuarioResumo consultarPerfil(String email, String senha);

}