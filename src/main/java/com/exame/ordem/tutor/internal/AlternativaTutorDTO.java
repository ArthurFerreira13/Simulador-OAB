package com.exame.ordem.tutor.internal;

public record AlternativaTutorDTO(
        Long id,
        String letra,
        String texto,
        boolean correta,
        String comentario
) {
    public boolean possuiComentario() {
        return comentario != null && !comentario.isBlank();
    }
}