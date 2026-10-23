package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import com.exame.ordem.tutor.config.CascataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Monta a lista de provedores na ordem do yaml; o Ollama (se ligado) entra por último. */
@Component
public class ProvedoresLlm {

    private static final Logger log = LoggerFactory.getLogger(ProvedoresLlm.class);

    private final List<LlmProvider> ordenados;

    ProvedoresLlm(CascataProperties props, ObjectProvider<OllamaLlmProvider> ollama) {
        List<LlmProvider> lista = new ArrayList<>();

        for (CascataProperties.Provedor cfg : props.provedores()) {
            if (!cfg.habilitado()) {
                log.info("Provedor LLM '{}' ignorado: sem api-key", cfg.id());
                continue;
            }
            lista.add(new OpenAiCompatibleLlmProvider(cfg));
            log.info("Provedor LLM '{}' habilitado (modelo={}, ttft={}, timeout={})",
                    cfg.id(), cfg.model(), cfg.ttft(), cfg.timeout());
        }

        ollama.ifAvailable(lista::add);

        if (lista.isEmpty()) {
            log.error("Nenhum provedor LLM habilitado. Defina ao menos uma API key no .env.");
        }
        this.ordenados = List.copyOf(lista);
    }

    public List<LlmProvider> lista() {
        return ordenados;
    }
}