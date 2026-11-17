package com.exame.ordem.tutor.internal;

public record QuestaoContexto(
        Integer id,
        String enunciado,
        boolean anulada,
        String provaTitulo,
        Integer ano
) {}