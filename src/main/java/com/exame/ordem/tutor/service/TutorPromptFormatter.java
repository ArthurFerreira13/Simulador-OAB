package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.SemanticCacheProperties;
import com.exame.ordem.tutor.internal.AlternativaTutorDTO;
import com.exame.ordem.tutor.internal.FonteRag;
import com.exame.ordem.tutor.internal.QuestaoContexto;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
class TutorPromptFormatter {

    private final SemanticCacheProperties cacheProps;

    TutorPromptFormatter(SemanticCacheProperties cacheProps) {
        this.cacheProps = cacheProps;
    }

    String montarRespostaBanco(QuestaoContexto ctx, List<AlternativaTutorDTO> alternativas) {
        var sb = new StringBuilder();

        if (ctx.anulada()) {
            sb.append("⚠️ QUESTÃO ANULADA PELA BANCA\n\n");
        } else {
            alternativas.stream()
                    .filter(AlternativaTutorDTO::correta)
                    .findFirst()
                    .ifPresent(correta -> sb.append("✅ RESPOSTA DIRETA\n\n")
                            .append("Gabarito: Alternativa ").append(correta.letra()).append("\n")
                            .append(correta.texto()).append("\n\n"));
        }

        sb.append("⚖️ FUNDAMENTO E JUSTIFICATIVA\n\n");
        alternativas.stream()
                .filter(AlternativaTutorDTO::possuiComentario)
                .forEach(alt -> sb.append("- Alternativa ")
                        .append(alt.letra())
                        .append(": ")
                        .append(alt.comentario())
                        .append("\n\n"));

        return sb.toString();
    }

    String montarPromptLlm(QuestaoContexto ctx,
                           List<AlternativaTutorDTO> alternativas,
                           List<FonteRag> fontes,
                           String pergunta) {
        var sb = new StringBuilder();
        sb.append("Questão: ").append(ctx.enunciado()).append("\n\n");

        if (ctx.anulada()) {
            sb.append("STATUS OFICIAL: QUESTÃO ANULADA PELA BANCA (não existe alternativa correta oficial).\n\n");
        } else {
            alternativas.stream()
                    .filter(AlternativaTutorDTO::correta)
                    .findFirst()
                    .ifPresent(c -> sb.append("Gabarito oficial: alternativa ").append(c.letra()).append("\n\n"));
        }

        sb.append("Alternativas e Comentários:\n");
        for (AlternativaTutorDTO alt : alternativas) {
            sb.append("- ").append(alt.letra()).append(") ").append(alt.texto()).append("\n");
            if (alt.possuiComentario()) {
                sb.append("  Comentário: ").append(alt.comentario()).append("\n");
            }
        }

        var semelhantes = fontes.stream()
                .filter(f -> f.origem() == FonteRag.Origem.SEMELHANTE)
                .toList();
        if (!semelhantes.isEmpty()) {
            sb.append("\nFontes Adicionais (Outras Questões):\n");
            semelhantes.forEach(f -> sb.append("- ").append(f.texto()).append("\n"));
        }

        // Entrada do aluno delimitada: o system prompt a trata como dado, nunca como instrução.
        sb.append("\n<duvida_do_aluno>\n").append(sanitizarEntrada(pergunta)).append("\n</duvida_do_aluno>");
        return sb.toString();
    }

    private static String sanitizarEntrada(String pergunta) {
        if (pergunta == null) return "";
        return pergunta.replaceAll("(?i)</?\\s*duvida_do_aluno\\s*>", "");
    }

    String gerarChaveCache(QuestaoContexto ctx, List<AlternativaTutorDTO> alternativas, String mensagem) {
        String base = cacheProps.promptVersion() + "|"
                + montarPromptLlm(ctx, alternativas, List.of(), normalizar(mensagem));
        return sha256(base);
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", " ").trim();
    }

    private static String sha256(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível na JVM", e);
        }
    }
}