package com.exame.ordem.questoes.service;

import com.exame.ordem.questoes.NumeroProvaExtractor;
import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.*;
import com.exame.ordem.questoes.internal.Alternativa;
import com.exame.ordem.questoes.internal.AlternativaRepository;
import com.exame.ordem.questoes.internal.DisciplinaRepository;
import com.exame.ordem.questoes.internal.Prova;
import com.exame.ordem.questoes.internal.ProvaRepository;
import com.exame.ordem.questoes.internal.Questao;
import com.exame.ordem.questoes.internal.QuestaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serviço de consulta de questões — SOMENTE LEITURA.
 * readOnly = true garante que o Hibernate não dispare flush nem UPDATE
 * por dirty checking, mesmo que alguém suje uma entidade por engano.
 */
@Service
@Transactional(readOnly = true)
class QuestoesQueryServiceImpl implements QuestoesQueryService {

    private final ProvaRepository provaRepository;
    private final QuestaoRepository questaoRepository;
    private final AlternativaRepository alternativaRepository;
    private final DisciplinaRepository disciplinaRepository;

    QuestoesQueryServiceImpl(ProvaRepository provaRepository,
                             QuestaoRepository questaoRepository,
                             AlternativaRepository alternativaRepository,
                             DisciplinaRepository disciplinaRepository) {
        this.provaRepository = provaRepository;
        this.questaoRepository = questaoRepository;
        this.alternativaRepository = alternativaRepository;
        this.disciplinaRepository = disciplinaRepository;
    }

    @Override
    public List<QuestaoResumo> buscarQuestoesPorProva(int numeroProva) {
        Prova prova = provaRepository.findAll().stream()
                .filter(p -> numeroProva == java.util.Objects.requireNonNullElse(
                        NumeroProvaExtractor.extrair(p.getTitulo()), -1))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Prova não encontrada para o número " + numeroProva));

        return mapearQuestoes(questaoRepository.findByProvaIdComAlternativas(prova.getId()));
    }

    @Override
    public RespostaDetalhadaDTO verificarResposta(int questaoId, Long alternativaId) {
        Questao questao = questaoRepository.findByIdInComAlternativas(List.of(questaoId)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Questão " + questaoId + " não encontrada"));

        Alternativa escolhida = questao.getAlternativas().stream()
                .filter(a -> a.getId().equals(alternativaId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Alternativa " + alternativaId + " não encontrada para a questão " + questaoId));

        List<AlternativaComGabarito> alternativas = questao.getAlternativas().stream()
                .map(a -> new AlternativaComGabarito(
                        a.getId(), a.getLetra(), a.getTexto(), a.getComentario(), a.isEhCorreta()))
                .toList();

        return new RespostaDetalhadaDTO(questaoId, alternativaId, escolhida.isEhCorreta(), alternativas);
    }

    @Override
    public boolean existeAlternativaNaQuestao(int questaoId, Long alternativaId) {
        return alternativaRepository.findByIdAndQuestaoId(alternativaId, questaoId).isPresent();
    }

    @Override
    public List<GabaritoResumo> buscarGabarito(List<Integer> questaoIds) {
        return questaoRepository.findByIdInComAlternativas(questaoIds).stream()
                .map(q -> new GabaritoResumo(
                        q.getId(),
                        q.getAlternativas().stream()
                                .filter(Alternativa::isEhCorreta)
                                .map(Alternativa::getId)
                                .findFirst()
                                .orElse(null)))
                .toList();
    }

    @Override
    public List<QuestaoResumo> buscarPorIds(List<Integer> questaoIds) {
        return mapearQuestoes(questaoRepository.findByIdInComAlternativas(questaoIds));
    }

    @Override
    public List<QuestaoResumo> buscarQuestoesPorDisciplina(Long disciplinaId) {
        return mapearQuestoes(questaoRepository.findByDisciplinaIdComAlternativas(disciplinaId));
    }

    @Override
    public List<QuestaoComGabaritoDTO> buscarComGabarito(List<Integer> questaoIds) {
        return questaoRepository.findByIdInComAlternativas(questaoIds).stream()
                .map(q -> new QuestaoComGabaritoDTO(
                        q.getId(),
                        q.getNumero(),
                        q.getEnunciado(),
                        q.isAnulada(),
                        q.getDisciplina() != null ? q.getDisciplina().getNome() : null,
                        q.getAlternativas().stream()
                                .map(a -> new AlternativaComGabarito(
                                        a.getId(), a.getLetra(), a.getTexto(), a.getComentario(), a.isEhCorreta()))
                                .toList()))
                .toList();
    }

    @Override
    public List<QuestaoResumo> buscarPorDisciplinaEAno(Long disciplinaId, Integer ano) {
        return mapearQuestoes(
                questaoRepository.findByDisciplinaEAnoComAlternativas(disciplinaId, ano));
    }

    // ==== novos ====

    @Override
    public List<ProvaResumo> listarProvas() {
        return provaRepository.findAll().stream()
                .map(p -> new ProvaResumo(p.getId(), p.getTitulo(), p.getAno()))
                .toList();
    }

    @Override
    public List<DisciplinaResumo> listarDisciplinas() {
        return disciplinaRepository.findAll().stream()
                .map(d -> new DisciplinaResumo(d.getId(), d.getNome()))
                .toList();
    }

    @Override
    public QuestaoResumo buscarQuestaoPorId(int questaoId) {
        Questao q = questaoRepository.findByIdInComAlternativas(List.of(questaoId)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Questão " + questaoId + " não encontrada"));
        return mapearQuestoes(List.of(q)).get(0);
    }

    private List<QuestaoResumo> mapearQuestoes(List<Questao> questoes) {
        return questoes.stream()
                .map(q -> new QuestaoResumo(
                        q.getId(), q.getProva().getId(), q.getNumero(), q.getEnunciado(), q.isAnulada(),
                        q.getDisciplina() != null ? q.getDisciplina().getNome() : null,
                        q.getAlternativas().stream()
                                .map(a -> new AlternativaResumo(a.getId(), a.getLetra(), a.getTexto()))
                                .toList()))
                .toList();
    }
}