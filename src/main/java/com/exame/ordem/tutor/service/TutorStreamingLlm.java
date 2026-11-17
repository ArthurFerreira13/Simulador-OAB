package com.exame.ordem.tutor.service;

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
                    if (sessao.isEncerrada()) return;
                    cota.registrarPrimeiroToken();
                    acumulador.append(token);
                    sessao.enviarEventoSilencioso("delta", token);
                })
                .onCompleteResponse(response -> {
                    int tokens = response.tokenUsage() != null
                            ? response.tokenUsage().totalTokenCount() : 0;
                    cota.confirmarConsumo(tokens);
                    cache.armazenar(chaveCache, mensagem, acumulador.toString(), questaoId);
                    sessao.concluirComSucesso();
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