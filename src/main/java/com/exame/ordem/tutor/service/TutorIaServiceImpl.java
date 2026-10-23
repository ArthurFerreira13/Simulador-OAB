package com.exame.ordem.tutor.service;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import com.exame.ordem.tutor.LlmProvider;
import com.exame.ordem.tutor.TutorIaService;
import com.exame.ordem.tutor.config.RagProperties;
import com.exame.ordem.tutor.dto.UsoTutorResumo;
import com.exame.ordem.tutor.internal.FonteRag;
import com.exame.ordem.tutor.internal.UsoTutorDia;
import com.exame.ordem.tutor.internal.UsoTutorDiaRepository;
import com.exame.ordem.usuarios.PoliticaAcessoService;
import com.exame.ordem.usuarios.RecursoAcesso;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
class TutorIaServiceImpl implements TutorIaService {

    private static final Logger log = LoggerFactory.getLogger(TutorIaServiceImpl.class);

    private static final int  LIMITE_TRIAL_MSG    = 30;
    private static final long LIMITE_TRIAL_TOKENS = 40_000L;
    private static final int  LIMITE_PAGO_MSG     = 60;
    private static final long LIMITE_PAGO_TOKENS  = 80_000L;
    private static final long SSE_TIMEOUT_MS      = 120_000L;

    private final UsoTutorDiaRepository usoRepository;
    private final PoliticaAcessoService politicaAcesso;
    private final QuestoesQueryService questoesQuery;
    private final LlmProvider llm;
    private final RagProviderResolver ragResolver;
    private final RagProperties ragProps;
    private final Clock clock;

    TutorIaServiceImpl(
            UsoTutorDiaRepository usoRepository,
            PoliticaAcessoService politicaAcesso,
            QuestoesQueryService questoesQuery,
            LlmProvider llm,
            RagProviderResolver ragResolver,
            RagProperties ragProps,
            Clock clock) {
        this.usoRepository = usoRepository;
        this.politicaAcesso = politicaAcesso;
        this.questoesQuery = questoesQuery;
        this.llm = llm;
        this.ragResolver = ragResolver;
        this.ragProps = ragProps;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SseEmitter conversar(Long usuarioId, Integer questaoId, String mensagem) {
        if (!politicaAcesso.permite(usuarioId, RecursoAcesso.TUTOR)) {
            throw new RegraDeNegocioException(
                    "Tutor de IA está disponível apenas no trial ou em um plano pago.");
        }

        Limites limites = limitesPara(usuarioId);
        LocalDate hoje = LocalDate.now(clock);

        // Reserva atômica ANTES de gastar LLM/RAG. Se algo abaixo lançar exceção, a transação
        // desfaz a reserva junto.
        reservarCota(usuarioId, hoje, limites);

        QuestaoResumo questao = questoesQuery.buscarQuestaoPorId(questaoId);

        // Comentários da própria questão + semelhantes (já sem os de triagem ERRADO/SUSPEITO)
        List<FonteRag> fontes = ragResolver.buscar(mensagem, questaoId);

        // System e user em mensagens separadas: Llama/Mistral seguem melhor o papel "system",
        // e a pergunta do aluno deixa de ficar colada às regras do tutor.
        List<ChatMessage> mensagens = List.of(
                SystemMessage.from(montarSystemPrompt()),
                UserMessage.from(montarUserPrompt(questao, mensagem, fontes)));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        enviarMeta(emitter, fontes); // RN09: o cliente sabe, antes do primeiro token, se há fonte
        llm.streaming().chat(mensagens, new SseStreamingHandler(emitter, usuarioId, hoje));
        return emitter;
    }

    @Override
    public UsoTutorResumo consultarUso(Long usuarioId) {
        Limites limites = limitesPara(usuarioId);
        LocalDate hoje = LocalDate.now(clock);
        UsoTutorDia uso = usoRepository.findByUsuarioIdAndData(usuarioId, hoje).orElse(null);

        int usadas = uso != null ? uso.getContador() : 0;
        long tokens = uso != null ? uso.getTokens() : 0L;

        return new UsoTutorResumo(
                usadas, tokens,
                limites.mensagens(), limites.tokens(),
                Math.max(0, limites.mensagens() - usadas));
    }

    private void reservarCota(Long usuarioId, LocalDate hoje, Limites limites) {
        LocalDateTime agora = LocalDateTime.now(clock);
        usoRepository.garantirLinha(usuarioId, hoje, agora);

        int reservou = usoRepository.reservar(
                usuarioId, hoje, limites.mensagens(), limites.tokens(), agora);
        if (reservou == 0) {
            UsoTutorDia uso = usoRepository.findByUsuarioIdAndData(usuarioId, hoje).orElseThrow();
            if (uso.getContador() >= limites.mensagens()) {
                throw new RegraDeNegocioException(
                        "Limite diário de mensagens do tutor atingido (" + limites.mensagens() + ").");
            }
            throw new RegraDeNegocioException(
                    "Limite diário de tokens do tutor atingido (" + limites.tokens() + ").");
        }
    }

    private Limites limitesPara(Long usuarioId) {
        return switch (politicaAcesso.estadoDe(usuarioId)) {
            case TRIAL -> new Limites(LIMITE_TRIAL_MSG, LIMITE_TRIAL_TOKENS);
            case PAGO  -> new Limites(LIMITE_PAGO_MSG, LIMITE_PAGO_TOKENS);
            case GRATUITO -> new Limites(0, 0);
        };
    }

    private String montarSystemPrompt() {
        return """
                Você é um Tutor de IA Sênior especialista na 1ª fase do Exame de Ordem (OAB/FGV).
                Seu objetivo é ensinar o aluno a raciocinar juridicamente e passar na prova.

                DIRETRIZES DE COMPORTAMENTO:
                1. BASE DE CONHECIMENTO: Use os "Comentários de Apoio" como sua base de verdade. NUNCA diga frases como "o comentário afirma que". Traduza o comentário para o conhecimento jurídico e explique a regra como se você fosse o professor.
                2. PREMISSA FALSA (REGRA CRÍTICA): Se o aluno perguntar "por que a alternativa X está errada" e a base mostrar que X está CORRETA, corrija-o educadamente e explique o fundamento jurídico.
                3. CITAÇÃO LEGAL: Sempre que houver um artigo de lei, cite-o expressamente.
                4. SEM ALUCINAÇÃO: Se os comentários não abordarem o tema, diga que a base atual não possui essa informação.
                5. OCULTE O RACIOCÍNIO: NÃO inclua blocos de "pensamento" ou tags de raciocínio na resposta final. Entregue APENAS o texto final que o aluno deve ler.

                FORMATO DA RESPOSTA (Obrigatório em Texto Limpo/Plain Text):
                NÃO USE asteriscos (**), cerquilhas (#), hífens (-) ou qualquer marcação de Markdown.
                Em vez disso, formate a resposta usando Letras MAIÚSCULAS para títulos, Emojis para organizar e Quebras de Linha duplas (Pule uma linha entre cada tópico).
                Estruture a resposta exatamente assim:

                ✅ RESPOSTA DIRETA
                (Diga se a alternativa está certa ou errada e por quê, em um parágrafo claro).

                ⚖️ FUNDAMENTO LEGAL
                (A regra jurídica explicada. Destaque o nome da lei em LETRAS MAIÚSCULAS).

                🎯 ANÁLISE DA BANCA (FGV)
                (Qual a pegadinha? O que o aluno precisa memorizar?).

                ❌ ANÁLISE DAS OUTRAS ALTERNATIVAS
                (Explique por que as outras estão erradas, apenas se houver base para isso).

                Seja detalhado e didático.
                """;
    }



    private String montarUserPrompt(QuestaoResumo questao, String pergunta, List<FonteRag> fontes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Questão ").append(questao.numero())
                .append(" — ").append(questao.disciplina()).append("\n\n");
        sb.append("Enunciado:\n").append(questao.enunciado()).append("\n\n");

        sb.append("Alternativas:\n");
        questao.alternativas().forEach(a ->
                sb.append(a.letra()).append(") ").append(a.texto()).append("\n"));

        if (!fontes.isEmpty()) {
            sb.append("\n--- Comentários de apoio ---\n");
            for (int i = 0; i < fontes.size(); i++) {
                FonteRag f = fontes.get(i);
                sb.append("[").append(i + 1).append("] ").append(rotulo(f)).append("\n")
                        .append(cortar(f.texto())).append("\n\n");
            }
        } else {
            sb.append("\n(Nenhum comentário de apoio disponível.)\n");
        }

        sb.append("\nPergunta: ").append(pergunta);
        return sb.toString();
    }

    private static String rotulo(FonteRag f) {
        String letra = f.letra() == null ? "?" : f.letra();
        return f.origem() == FonteRag.Origem.QUESTAO
                ? "Comentário da alternativa " + letra + " desta questão:"
                : "Comentário de outra questão relacionada (alternativa " + letra + "):";
    }

    private String cortar(String texto) {
        int max = ragProps.maxCaracteresPorFonte();
        return texto.length() <= max ? texto : texto.substring(0, max) + "…";
    }

    /** Evento "meta" (RN09): só ids, letra e origem. O texto dos comentários não vai ao cliente. */
    private void enviarMeta(SseEmitter emitter, List<FonteRag> fontes) {
        StringBuilder json = new StringBuilder("{\"semFonte\":").append(fontes.isEmpty())
                .append(",\"quantidade\":").append(fontes.size())
                .append(",\"fontes\":[");
        for (int i = 0; i < fontes.size(); i++) {
            FonteRag f = fontes.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"alternativaId\":").append(f.alternativaId())
                    .append(",\"questaoId\":").append(f.questaoId())
                    .append(",\"letra\":").append(jsonLetra(f.letra()))
                    .append(",\"origem\":\"").append(f.origem().name()).append("\"}");
        }
        json.append("]}");
        try {
            emitter.send(SseEmitter.event().name("meta").data(json.toString()));
        } catch (Exception e) {
            log.debug("Cliente desconectou antes do evento meta: {}", e.getMessage());
        }
    }

    private static String jsonLetra(String letra) {
        return letra != null && letra.matches("[A-Za-z]") ? "\"" + letra + "\"" : "null";
    }

    /** Consumo real informado pelo provedor; estimativa por tamanho só quando ele não informa. */
    private static int tokensConsumidos(ChatResponse response, int caracteresGerados) {
        var u = response.tokenUsage();
        if (u != null && u.totalTokenCount() != null && u.totalTokenCount() > 0) {
            return u.totalTokenCount();
        }
        return Math.max(1, caracteresGerados / 4);
    }

    private record Limites(int mensagens, long tokens) {}

    private class SseStreamingHandler implements StreamingChatResponseHandler {
        private final SseEmitter emitter;
        private final Long usuarioId;
        private final LocalDate dia; // dia da reserva: o fechamento usa o mesmo dia, mesmo virando a meia-noite
        private final StringBuilder buffer = new StringBuilder();
        private volatile boolean clienteConectado = true;

        SseStreamingHandler(SseEmitter emitter, Long usuarioId, LocalDate dia) {
            this.emitter = emitter;
            this.usuarioId = usuarioId;
            this.dia = dia;
        }

        @Override
        public void onPartialResponse(String partial) {
            buffer.append(partial); // continua somando: o consumo é cobrado mesmo sem cliente
            if (!clienteConectado) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("delta").data(partial));
            } catch (Exception e) {
                clienteConectado = false;
                emitter.completeWithError(e);
            }
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
            try {
                int tokens = tokensConsumidos(response, buffer.length());
                usoRepository.somarTokens(usuarioId, dia, tokens, LocalDateTime.now(clock));

                if (clienteConectado) {
                    emitter.send(SseEmitter.event().name("completed")
                            .data("{\"tokens\":" + tokens + "}"));
                    emitter.complete();
                }
            } catch (Exception e) {
                log.warn("Falha ao finalizar SSE", e);
                emitter.completeWithError(e);
            }
        }

        @Override
        public void onError(Throwable error) {
            log.warn("Erro do LLM: {}", error.getMessage());
            try {
                LocalDateTime agora = LocalDateTime.now(clock);
                if (buffer.length() == 0) {
                    usoRepository.estornar(usuarioId, dia, agora); // nada entregue: devolve a mensagem
                } else {
                    usoRepository.somarTokens(usuarioId, dia, Math.max(1, buffer.length() / 4), agora);
                }
            } catch (Exception e) {
                log.warn("Falha ao ajustar a cota após erro do LLM", e);
            }
            if (!clienteConectado) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data("Tutor indisponível no momento."));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }
    }
}