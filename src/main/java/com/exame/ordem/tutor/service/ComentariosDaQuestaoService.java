package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.RagProperties;
import com.exame.ordem.tutor.internal.ComentariosRepository;
import com.exame.ordem.tutor.internal.FonteRag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ComentariosDaQuestaoService {

    private static final Logger log = LoggerFactory.getLogger(ComentariosDaQuestaoService.class);

    private final ComentariosRepository comentarios;
    private final RagProperties props;

    public ComentariosDaQuestaoService(ComentariosRepository comentarios, RagProperties props) {
        this.comentarios = comentarios;
        this.props = props;
    }

    public List<FonteRag> buscar(Integer questaoId) {
        try {
            return comentarios.daQuestao(questaoId, props.statusBloqueados());
        } catch (Exception e) {
            log.error("Falha ao ler comentários da questão {}: {}", questaoId, e.getMessage());
            return List.of();
        }
    }
}