package com.exame.ordem.questoes.service;

import com.exame.ordem.questoes.NumeroProvaExtractor;
import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.AlternativaResumo;
import com.exame.ordem.questoes.dto.GabaritoResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.questoes.internal.Alternativa;
import com.exame.ordem.questoes.internal.AlternativaRepository;
import com.exame.ordem.questoes.internal.Prova;
import com.exame.ordem.questoes.internal.ProvaRepository;
import com.exame.ordem.questoes.internal.Questao;
import com.exame.ordem.questoes.internal.QuestaoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class QuestoesQueryServiceImpl implements QuestoesQueryService {

    private final ProvaRepository provaRepository;
    private final QuestaoRepository questaoRepository;
    private final AlternativaRepository alternativaRepository;

    QuestoesQueryServiceImpl(ProvaRepository provaRepository, QuestaoRepository questaoRepository, AlternativaRepository alternativaRepository) {
        this.provaRepository = provaRepository;
        this.questaoRepository = questaoRepository;
        this.alternativaRepository = alternativaRepository;
    }

    @Override
    public List<QuestaoResumo> buscarQuestoesPorProva(int numeroProva) {
        Prova prova = provaRepository.findAll().stream()
                .filter(p -> Integer.valueOf(numeroProva).equals(NumeroProvaExtractor.extrair(p.getTitulo())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Prova " + numeroProva + " não encontrada."));

        return toResumo(questaoRepository.findByProvaIdComAlternativas(prova.getId()));
    }

    @Override
    public boolean verificarResposta(int questaoId, Long alternativaId) {
        Alternativa alternativa = alternativaRepository.findByIdAndQuestaoId(alternativaId, questaoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Alternativa " + alternativaId + " não pertence à questão " + questaoId));
        return alternativa.isEhCorreta();
    }

    @Override
    public List<GabaritoResumo> buscarGabarito(List<Integer> questaoIds) {
        return questaoRepository.findByIdIn(questaoIds).stream()
                .map(q -> {
                    Long corretaId = q.getAlternativas().stream()
                            .filter(Alternativa::isEhCorreta)
                            .findFirst()
                            .map(Alternativa::getId)
                            .orElse(null); // null = questão anulada, sem gabarito
                    return new GabaritoResumo(q.getId(), corretaId);
                })
                .toList();
    }

    private List<QuestaoResumo> toResumo(List<Questao> questoes) {
        return questoes.stream()
                .map(q -> new QuestaoResumo(
                        q.getId(), q.getProva().getId(), q.getNumero(), q.getEnunciado(), q.isAnulada(),
                        q.getAlternativas().stream()
                                .map(a -> new AlternativaResumo(a.getId(), a.getLetra(), a.getTexto()))
                                .toList()))
                .toList();
    }
}