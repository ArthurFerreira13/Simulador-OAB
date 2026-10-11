package com.exame.ordem.questoes.service;

import com.exame.ordem.questoes.GabaritoService;
import com.exame.ordem.questoes.dto.AlternativaComGabarito;
import com.exame.ordem.questoes.dto.GabaritoResumo;
import com.exame.ordem.questoes.dto.QuestaoComGabaritoDTO;
import com.exame.ordem.questoes.dto.QuestaoRevisadaDTO;
import com.exame.ordem.questoes.dto.RespostaDetalhadaDTO;
import com.exame.ordem.questoes.internal.Alternativa;
import com.exame.ordem.questoes.internal.AlternativaRepository;
import com.exame.ordem.questoes.internal.Questao;
import com.exame.ordem.questoes.internal.QuestaoRepository;
import com.exame.ordem.system.erro.excecoes.RecursoNaoEncontradoException;
import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Implementação de gabarito e verificação de resposta.
 * Somente leitura — não persiste nada.
 */
@Service
@Transactional(readOnly = true)
class GabaritoServiceImpl implements GabaritoService {

    private final QuestaoRepository questaoRepository;
    private final AlternativaRepository alternativaRepository;

    GabaritoServiceImpl(QuestaoRepository questaoRepository,
                        AlternativaRepository alternativaRepository) {
        this.questaoRepository = questaoRepository;
        this.alternativaRepository = alternativaRepository;
    }

    @Override
    public RespostaDetalhadaDTO verificarResposta(int questaoId, String letra) {
        Questao questao = questaoRepository.findByIdInComAlternativas(List.of(questaoId)).stream()
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Questão " + questaoId + " não encontrada"));

        Alternativa escolhida = questao.getAlternativas().stream()
                .filter(a -> a.getLetra().equals(letra))
                .findFirst()
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Alternativa " + letra + " não existe na questão " + questaoId));

        List<AlternativaComGabarito> alternativas = questao.getAlternativas().stream()
                .map(a -> new AlternativaComGabarito(
                        a.getId(), a.getLetra(), a.getTexto(), a.getComentario(), a.isEhCorreta()))
                .toList();

        return new RespostaDetalhadaDTO(questaoId, escolhida.getId(), escolhida.isEhCorreta(), alternativas);
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
    public List<QuestaoRevisadaDTO> montarRevisao(List<Integer> questaoIds,
                                                  Map<Integer, Long> respostas) {
        return questaoRepository.findByIdInComAlternativas(questaoIds).stream()
                .map(q -> {
                    Long escolhida = respostas.get(q.getId());

                    boolean correta = q.getAlternativas().stream()
                            .anyMatch(a -> a.isEhCorreta() && a.getId().equals(escolhida));

                    QuestaoComGabaritoDTO questaoDTO = new QuestaoComGabaritoDTO(
                            q.getId(),
                            q.getNumero(),
                            q.getEnunciado(),
                            q.isAnulada(),
                            q.getDisciplina() != null ? q.getDisciplina().getNome() : null,
                            q.getAlternativas().stream()
                                    .map(a -> new AlternativaComGabarito(
                                            a.getId(), a.getLetra(), a.getTexto(), a.getComentario(), a.isEhCorreta()))
                                    .toList());

                    return new QuestaoRevisadaDTO(questaoDTO, escolhida, correta);
                })
                .toList();
    }
}