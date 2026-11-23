package com.exame.ordem.tutor.service;

import java.util.regex.Pattern;

/**
 * Heurística barata contra prompt injection. Perguntas suspeitas continuam sendo respondidas,
 * mas a resposta NÃO entra nos caches compartilhados (exato e semântico): um aluno não pode
 * plantar uma resposta manipulada para os demais.
 */
final class PerguntaSuspeita {

    private static final Pattern PADRAO = Pattern.compile(
            "ignor[ea]\\s+(todas?\\s+)?(as\\s+)?(instrucoes|regras|orientacoes|diretrizes)"
                    + "|ignore\\s+(all\\s+|the\\s+|any\\s+)?(previous|prior|above|earlier)"
                    + "|desconsidere\\s+(as\\s+)?(instrucoes|regras|orientacoes)"
                    + "|esqueca\\s+(as\\s+)?(instrucoes|regras)"
                    + "|(system|developer)\\s+prompt"
                    + "|prompt\\s+do\\s+sistema"
                    + "|instrucoes\\s+do\\s+sistema"
                    + "|voce\\s+agora\\s+e\\b"
                    + "|you\\s+are\\s+now\\b"
                    + "|finja\\s+(que\\s+)?(ser|voce)"
                    + "|\\bdan\\s+mode\\b|\\bjailbreak\\b");

    private PerguntaSuspeita() {}

    static boolean parece(String mensagem) {
        if (mensagem == null) return false;
        // A tag que delimita a entrada do aluno é checada no texto cru: a normalização apaga o "_".
        if (mensagem.toLowerCase(java.util.Locale.ROOT).contains("duvida_do_aluno")) return true;
        return PADRAO.matcher(EmbeddingDaPergunta.normalizar(mensagem)).find();
    }
}
