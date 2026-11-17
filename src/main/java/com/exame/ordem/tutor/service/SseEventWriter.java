package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.internal.FonteRag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class SseEventWriter {

    private static final Logger log = LoggerFactory.getLogger(SseEventWriter.class);
    private final ObjectMapper objectMapper;

    public SseEventWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void enviarMeta(SseEmitter emitter, List<FonteRag> fontes) {
        var itens = fontes.stream()
                .map(f -> new FonteMetaItem(
                        f.alternativaId(),
                        f.questaoId(),
                        normalizarLetra(f.letra()),
                        f.origem().name()
                ))
                .toList();

        var payload = new MetaPayload(itens.isEmpty(), itens.size(), itens);

        try {
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name("meta").data(json));
        } catch (Exception e) {
            log.debug("Cliente desconectou antes do evento meta: {}", e.getMessage());
        }
    }

    private static String normalizarLetra(String letra) {
        return letra != null && letra.matches("[A-Za-z]") ? letra : null;
    }

    private record MetaPayload(boolean semFonte, int quantidade, List<FonteMetaItem> fontes) {}

    private record FonteMetaItem(Long alternativaId, Integer questaoId, String letra, String origem) {}
}