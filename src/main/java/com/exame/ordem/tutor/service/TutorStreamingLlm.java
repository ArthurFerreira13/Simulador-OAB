package com.exame.ordem.tutor.service;

import dev.langchain4j.model.output.FinishReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class TutorStreamingLlm {

    private static final Logger log = LoggerFactory.getLogger(TutorStreamingLlm.class);

    private static final String MSG_ERRO =
            "{\"mensagem\":\"Explicação extra indisponível, mas o gabarito acima está correto.\"}";

    private final TutorAiAssistant aiAssistant;
    private final TutorCacheFacade cache;

    TutorStreamingLlm(TutorAiAssistant aiAssistant, TutorCacheFacade cache) {
        this.aiAssistant = aiAssistant;
        this.cache = cache;
    }

    void iniciar(SessaoSse sessao, CotaSessao cota, String prompt,
                 String chaveCache, Integer questaoId, String mensagem) {
        var acumulador = new StringBuilder();

        aiAssistant.conversar(prompt)
                .onPartialResponse(token -> {
                    // Acumula SEMPRE: mesmo com o cliente fora, a resposta precisa saber se está completa.
                    cota.registrarToken();
                    acumulador.append(token);
                    if (!sessao.isEncerrada()) {
                        sessao.enviarEventoSilencioso("delta", token);
                    }
                })
                .onCompleteResponse(response -> {
                    int tokens = response.tokenUsage() != null
                            ? response.tokenUsage().totalTokenCount() : 0;
                    cota.confirmarConsumo(tokens);

                    // Só vai para os caches compartilhados uma resposta inteira e entregue ao aluno:
                    // sessão ainda aberta (sem timeout/desconexão) e término natural do modelo.
                    FinishReason motivo = response.finishReason();
                    boolean terminouNormalmente = motivo == null || motivo == FinishReason.STOP;
                    boolean sessaoAberta = !sessao.isEncerrada();
                    boolean cacheavel = terminouNormalmente && sessaoAberta;

                    sessao.concluirComSucesso();   // o aluno não espera a gravação do cache

                    if (cacheavel) {
                        cache.armazenar(chaveCache, mensagem, acumulador.toString(), questaoId);
                    } else {
                        log.debug("Resposta não cacheada (questao={}, finishReason={}, sessaoEncerrada={})",
                                questaoId, motivo, !sessaoAberta);
                    }
                })
                .onError(error -> {
                    log.error("Falha na cascata LLM (usuario={})", cota.usuarioId(), error);
                    cota.encerrarComEstornoSeNecessario();
                    sessao.enviarEventoSilencioso("error", MSG_ERRO);
                    sessao.concluirSemEvento();
                })
                .start();
    }
}
