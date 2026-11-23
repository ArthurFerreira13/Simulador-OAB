package com.exame.ordem.tutor.internal;

public record AlternativaTutorDTO(
        Long id,
        String letra,
        String texto,
        boolean correta,
        String comentario,
        String comentarioStatus
) {
    public boolean possuiComentario() {
        return comentario != null && !comentario.isBlank();
    }

    /** Mesma alternativa sem o comentário (usado quando a triagem bloqueou o comentário). */
    public AlternativaTutorDTO semComentario() {
        return new AlternativaTutorDTO(id, letra, texto, correta, null, comentarioStatus);
    }
}
