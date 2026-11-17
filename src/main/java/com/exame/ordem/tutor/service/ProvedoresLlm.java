package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import com.exame.ordem.tutor.config.CascataProperties;
import com.exame.ordem.tutor.config.TutorProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProvedoresLlm {

    private static final Logger log = LoggerFactory.getLogger(ProvedoresLlm.class);
    private final List<LlmProvider> provedores;

    ProvedoresLlm(CascataProperties props,
                  TutorProperties tutorProperties,
                  ObjectProvider<OllamaLlmProvider> ollamaProvider,
                  ObjectProvider<FreeLlmApiLlmProvider> freeLlmProvider) {
        List<LlmProvider> configurados = new ArrayList<>(props.provedores().stream()
                .filter(CascataProperties.Provedor::habilitado)
                .<LlmProvider>map(OpenAiCompatibleLlmProvider::new)
                .toList());

        OllamaLlmProvider ollama = ollamaProvider.getIfAvailable();
        if (ollama != null) {
            configurados.add(ollama);
        }

        var freeLlmCfg = tutorProperties.freellmapi();
        if (freeLlmCfg.autostart() || freeLlmCfg.habilitado()) {
            FreeLlmApiLlmProvider freeLlm = freeLlmProvider.getIfAvailable();
            if (freeLlm != null) {
                configurados.add(freeLlm);
            }
        }

        this.provedores = List.copyOf(configurados);
        log.info("Provedores carregados: {}", provedores.stream().map(LlmProvider::name).toList());
    }

    public List<LlmProvider> getProvedores() {
        return provedores;
    }

    public int maxTokensResposta() {
        return provedores.stream()
                .mapToInt(LlmProvider::maxOutputTokens)
                .max()
                .orElse(1);
    }
}