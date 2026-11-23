package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import com.exame.ordem.tutor.config.CascataProperties;
import com.exame.ordem.tutor.config.TutorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProvedoresLlm {

    private static final Logger log = LoggerFactory.getLogger(ProvedoresLlm.class);
    private final List<LlmProvider> provedores;

    ProvedoresLlm(CascataProperties props,
                  TutorProperties tutorProperties,
                  ObjectProvider<OllamaLlmProvider> ollamaProvider,
                  ObjectProvider<FreeLlmApiLlmProvider> freeLlmProvider) {

        // Providers dedicados (têm classe própria e prioridade sobre o YAML)
        List<LlmProvider> dedicados = new ArrayList<>();

        OllamaLlmProvider ollama = ollamaProvider.getIfAvailable();
        if (ollama != null) {
            dedicados.add(ollama);
        }

        var freeLlmCfg = tutorProperties.freellmapi();
        if (freeLlmCfg.autostart() || freeLlmCfg.habilitado()) {
            FreeLlmApiLlmProvider freeLlm = freeLlmProvider.getIfAvailable();
            if (freeLlm != null) {
                dedicados.add(freeLlm);
            }
        }

        Set<String> nomesDedicados = dedicados.stream()
                .map(LlmProvider::name)
                .collect(Collectors.toSet());

        // Providers vindos do YAML, ignorando os que já têm versão dedicada
        List<LlmProvider> configurados = props.provedores().stream()
                .filter(CascataProperties.Provedor::habilitado)
                .<LlmProvider>map(OpenAiCompatibleLlmProvider::new)
                .filter(p -> {
                    boolean duplicado = nomesDedicados.contains(p.name());
                    if (duplicado) {
                        log.warn("Provedor '{}' do YAML ignorado: já existe provider dedicado com o mesmo nome.",
                                p.name());
                    }
                    return !duplicado;
                })
                .collect(Collectors.toCollection(ArrayList::new));

        configurados.addAll(dedicados);

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