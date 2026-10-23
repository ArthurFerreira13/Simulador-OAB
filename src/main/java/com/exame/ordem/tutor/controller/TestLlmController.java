package com.exame.ordem.tutor.controller;

import com.exame.ordem.tutor.LlmProvider;
import com.exame.ordem.tutor.service.ProvedoresLlm;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Testa cada provedor isolado (ignora a cascata). Só no profile dev: consome cota gratuita. */
@Profile("dev")
@RestController
@RequestMapping("/api/test-llm")
public class TestLlmController {

    private static final Logger log = LoggerFactory.getLogger(TestLlmController.class);

    private final Map<String, LlmProvider> provedores;

    public TestLlmController(ProvedoresLlm provedoresLlm) {
        this.provedores = provedoresLlm.lista().stream()
                .collect(Collectors.toMap(p -> chave(p.name()), Function.identity()));
    }

    @GetMapping("/{provedor}")
    public SseEmitter testar(@PathVariable String provedor, @RequestParam String msg) {
        SseEmitter emitter = new SseEmitter(60_000L);
        LlmProvider llm = provedores.get(chave(provedor));

        if (llm == null) {
            enviar(emitter, "Provedor não encontrado: " + provedor + ". Disponíveis: " + provedores.keySet());
            return emitter;
        }

        long inicioNs = System.nanoTime();
        AtomicBoolean primeiro = new AtomicBoolean();

        try {
            llm.streaming().chat(List.of(UserMessage.from(msg)), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String parcial) {
                    if (primeiro.compareAndSet(false, true)) {
                        log.info("{} ttftMs={}", llm.name(), (System.nanoTime() - inicioNs) / 1_000_000);
                    }
                    try {
                        emitter.send(SseEmitter.event().data(parcial));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse resposta) {
                    log.info("{} totalMs={}", llm.name(), (System.nanoTime() - inicioNs) / 1_000_000);
                    emitter.complete();
                }

                @Override
                public void onError(Throwable erro) {
                    log.warn("{} falhou: {}", llm.name(), erro.toString());
                    enviar(emitter, "Erro em " + llm.name() + ": " + erro);
                }
            });
        } catch (Exception e) {
            enviar(emitter, "Não foi possível chamar " + llm.name() + ": " + e);
        }
        return emitter;
    }

    private static String chave(String nome) {
        return nome.trim().toLowerCase(Locale.ROOT);
    }

    private static void enviar(SseEmitter emitter, String mensagem) {
        try {
            emitter.send(SseEmitter.event().data(mensagem));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
}