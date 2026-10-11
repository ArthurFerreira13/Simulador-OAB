package com.exame.ordem.questoes.service;

import com.exame.ordem.questoes.NumeroProvaExtractor;
import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.*;
import com.exame.ordem.questoes.internal.DisciplinaRepository;
import com.exame.ordem.questoes.internal.Prova;
import com.exame.ordem.questoes.internal.ProvaRepository;
import com.exame.ordem.questoes.internal.Questao;
import com.exame.ordem.questoes.internal.QuestaoRepository;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serviço de consulta e listagem de questões — SOMENTE LEITURA.
 *
 * Verificação de resposta e gabarito foram movidas para GabaritoService.
 */
@Service
@Transactional(readOnly = true)
class QuestoesQueryServiceImpl implements QuestoesQueryService {

    private final ProvaRepository provaRepository;
    private final QuestaoRepository questaoRepository;
    private final DisciplinaRepository disciplinaRepository;

    QuestoesQueryServiceImpl(ProvaRepository provaRepository,
                             QuestaoRepository questaoRepository,
                             DisciplinaRepository disciplinaRepository) {
        this.provaRepository = provaRepository;
        this.questaoRepository = questaoRepository;
        this.disciplinaRepository = disciplinaRepository;
    }

    @Override
    public List<QuestaoResumo> buscarQuestoesPorProva(int numeroProva) {
        Prova prova = provaRepository.findAll().stream()
                .filter(p -> numeroProva == java.util.Objects.requireNonNullElse(
                        NumeroProvaExtractor.extrair(p.getTitulo()), -1))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Prova não encontrada para o número " + numeroProva));

        return mapearQuestoes(questaoRepository.findByProvaIdComAlternativas(prova.getId()));
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
    public List<QuestaoResumo> buscarPorDisciplinaEAno(Long disciplinaId, Integer ano) {
        return mapearQuestoes(
                questaoRepository.findByDisciplinaEAnoComAlternativas(disciplinaId, ano));
    }

    @Override
    public QuestaoResumo buscarQuestaoPorId(int questaoId) {
        Questao q = questaoRepository.findByIdInComAlternativas(List.of(questaoId)).stream()
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Questão " + questaoId + " não encontrada"));
        return mapearQuestoes(List.of(q)).get(0);
    }

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