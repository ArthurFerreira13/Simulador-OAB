package com.exame.ordem.legislacao.internal;

import java.util.UUID;

/** Candidato da busca vetorial; a hidratação do trecho citável fica a cargo da etapa de fusão. */
public record CandidatoVetorial(UUID versaoId, double relevancia) {}