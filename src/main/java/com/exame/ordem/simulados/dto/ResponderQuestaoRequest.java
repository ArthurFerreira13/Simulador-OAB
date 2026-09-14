package com.exame.ordem.simulados.dto;

import jakarta.validation.constraints.NotNull;

public record ResponderQuestaoRequest(@NotNull Long alternativaId) {}