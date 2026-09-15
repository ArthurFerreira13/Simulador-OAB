package com.exame.ordem.simulados.service;

import com.exame.ordem.questoes.QuestoesQueryService;
import com.exame.ordem.questoes.dto.GabaritoResumo;
import com.exame.ordem.questoes.dto.QuestaoResumo;
import com.exame.ordem.simulados.SimuladoService;
import com.exame.ordem.simulados.dto.*;
import com.exame.ordem.simulados.internal.*;
import com.exame.ordem.usuarios.UsuarioService;
import com.exame.ordem.usuarios.dto.UsuarioResumo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
class SimuladoServiceImpl implements SimuladoService {

    private static final int TEMPO_LIMITE_SEGUNDOS = 5 * 60 * 60;
    private static final BigDecimal NOTA_CORTE = BigDecimal.valueOf(50);
    private static final int DIAS_TESTE_GRATIS = 7;

    private final SimuladoSessaoRepository sessaoRepository;
    private final SimuladoSessaoQuestaoRepository sessaoQuestaoRepository;
    private final RespostaSimuladoRepository respostaRepository;
    private final ResultadoSimuladoRepository resultadoRepository;
    private final QuestoesQueryService questoesQueryService;
    private final UsuarioService usuarioService;

    SimuladoServiceImpl(SimuladoSessaoRepository sessaoRepository,
                        SimuladoSessaoQuestaoRepository sessaoQuestaoRepository,
                        RespostaSimuladoRepository respostaRepository,
                        ResultadoSimuladoRepository resultadoRepository,
                        QuestoesQueryService questoesQueryService,
                        UsuarioService usuarioService) {
        this.sessaoRepository = sessaoRepository;
        this.sessaoQuestaoRepository = sessaoQuestaoRepository;
        this.respostaRepository = respostaRepository;
        this.resultadoRepository = resultadoRepository;
        this.questoesQueryService = questoesQueryService;
        this.usuarioService = usuarioService;
    }

    @Override
    @Transactional
    public SessaoDTO iniciarSimulado(IniciarSimuladoRequest request) {
        UsuarioResumo usuario = usuarioService.consultarPerfil(request.email(), request.senha());

        sessaoRepository.findByUsuarioIdAndStatus(usuario.id(), StatusSessao.EM_ANDAMENTO)
                .ifPresent(s -> {
                    if (s.tempoExpirado()) {
                        s.abandonar();
                        sessaoRepository.save(s);
                    } else {
                        throw new IllegalStateException("Já existe simulado em andamento.");
                    }
                });

        verificarLimiteDeUso(usuario);

        List<QuestaoResumo> questoes = questoesQueryService.buscarQuestoesPorProva(request.numeroProva());

        SimuladoSessao sessao = SimuladoSessao.iniciar(usuario.id(), questoes.get(0).provaId(), TEMPO_LIMITE_SEGUNDOS);
        sessao = sessaoRepository.save(sessao);

        int ordem = 1;
        for (QuestaoResumo q : questoes) {
            sessaoQuestaoRepository.save(new SimuladoSessaoQuestao(sessao.getId(), q.id(), ordem++));
        }

        return montarSessaoDTO(sessao, questoes, Map.of());
    }

    private void verificarLimiteDeUso(UsuarioResumo usuario) {
        LocalDateTime fimDoTeste = usuario.criadoEm().plusDays(DIAS_TESTE_GRATIS);
        boolean dentroDoTeste = LocalDateTime.now(ZoneOffset.UTC).isBefore(fimDoTeste);
        if (dentroDoTeste) {
            return;
        }

        LocalDateTime inicioDoDia = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        long simuladosHoje = sessaoRepository.countByUsuarioIdAndIniciadoEmAfter(usuario.id(), inicioDoDia);
        if (simuladosHoje >= 1) {
            throw new IllegalStateException(
                    "Limite diário de simulados atingido. Assine um plano para acesso ilimitado.");
        }
    }

    @Override
    @Transactional
    public void responderQuestao(Long sessaoId, int questaoId, ResponderQuestaoRequest request) {
        SimuladoSessao sessao = sessaoRepository.findById(sessaoId)
                .orElseThrow(() -> new IllegalArgumentException("Sessão não encontrada."));

        if (sessao.getStatus() != StatusSessao.EM_ANDAMENTO) {
            throw new IllegalStateException("Sessão não está em andamento.");
        }

        if (sessao.tempoExpirado()) {
            sessao.abandonar();
            sessaoRepository.save(sessao);
            throw new IllegalStateException("Tempo da sessão expirado. Sessão marcada como abandonada.");
        }

        respostaRepository.findBySessaoIdAndQuestaoId(sessaoId, questaoId)
                .ifPresentOrElse(
                        r -> r.atualizarResposta(request.alternativaId()),
                        () -> respostaRepository.save(new RespostaSimulado(sessaoId, questaoId, request.alternativaId()))
                );
    }

    @Override
    @Transactional
    public ResultadoDTO finalizarSimulado(Long sessaoId) {
        SimuladoSessao sessao = sessaoRepository.findById(sessaoId)
                .orElseThrow(() -> new IllegalArgumentException("Sessão não encontrada."));

        if (sessao.getStatus() != StatusSessao.EM_ANDAMENTO) {
            throw new IllegalStateException("Esta sessão já foi finalizada.");
        }

        List<SimuladoSessaoQuestao> questoesDaSessao = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessaoId);
        List<Integer> questaoIds = questoesDaSessao.stream().map(SimuladoSessaoQuestao::getQuestaoId).toList();

        Map<Integer, Long> gabarito = questoesQueryService.buscarGabarito(questaoIds).stream()
                .collect(Collectors.toMap(GabaritoResumo::questaoId, GabaritoResumo::alternativaCorretaId, (a, b) -> a));

        Map<Integer, Long> respostas = respostaRepository.findBySessaoId(sessaoId).stream()
                .collect(Collectors.toMap(RespostaSimulado::getQuestaoId, RespostaSimulado::getAlternativaId));

        int acertos = 0, erros = 0, brancos = 0;
        for (Integer qId : questaoIds) {
            Long respondida = respostas.get(qId);
            Long correta = gabarito.get(qId);
            if (respondida == null) {
                brancos++;
            } else if (correta != null && correta.equals(respondida)) {
                acertos++;
            } else {
                erros++;
            }
        }

        int total = questaoIds.size();
        BigDecimal percentual = BigDecimal.valueOf(acertos)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        boolean aprovado = percentual.compareTo(NOTA_CORTE) >= 0;

        sessao.finalizar();
        sessaoRepository.save(sessao);

        ResultadoSimulado resultado = new ResultadoSimulado(
                sessaoId, total, acertos, erros, brancos, percentual, aprovado, (int) sessao.tempoGastoSegundos());
        resultadoRepository.save(resultado);

        return toResultadoDTO(resultado);
    }

    @Override
    @Transactional
    public SessaoDTO consultarSessaoAtiva(String email, String senha) {
        UsuarioResumo usuario = usuarioService.consultarPerfil(email, senha);

        SimuladoSessao sessao = sessaoRepository.findByUsuarioIdAndStatus(usuario.id(), StatusSessao.EM_ANDAMENTO)
                .orElseThrow(() -> new IllegalArgumentException("Nenhum simulado em andamento."));

        if (sessao.tempoExpirado()) {
            sessao.abandonar();
            sessaoRepository.save(sessao);
            throw new IllegalArgumentException("Nenhum simulado em andamento.");
        }

        List<SimuladoSessaoQuestao> vinculos = sessaoQuestaoRepository.findBySessaoIdOrderByOrdemAsc(sessao.getId());
        List<Integer> questaoIds = vinculos.stream().map(SimuladoSessaoQuestao::getQuestaoId).toList();
        List<QuestaoResumo> questoes = questoesQueryService.buscarPorIds(questaoIds);

        Map<Integer, Long> respostas = respostaRepository.findBySessaoId(sessao.getId()).stream()
                .collect(Collectors.toMap(RespostaSimulado::getQuestaoId, RespostaSimulado::getAlternativaId));

        return montarSessaoDTO(sessao, questoes, respostas);
    }

    @Override
    public ResultadoDTO consultarResultado(Long sessaoId) {
        ResultadoSimulado resultado = resultadoRepository.findBySessaoId(sessaoId)
                .orElseThrow(() -> new IllegalArgumentException("Resultado ainda não calculado."));
        return toResultadoDTO(resultado);
    }

    private SessaoDTO montarSessaoDTO(SimuladoSessao sessao, List<QuestaoResumo> questoes, Map<Integer, Long> respostas) {
        List<QuestaoSessaoDTO> dto = questoes.stream()
                .map(q -> new QuestaoSessaoDTO(
                        q.id(), q.numero(), q.enunciado(), q.anulada(), q.disciplina(),
                        q.alternativas().stream()
                                .map(a -> new AlternativaSessaoDTO(a.id(), a.letra(), a.texto()))
                                .toList(),
                        respostas.get(q.id())))
                .toList();
        return new SessaoDTO(sessao.getId(), sessao.getStatus().name(), sessao.getTempoLimiteSegundos(), sessao.getIniciadoEm(), dto);
    }

    private ResultadoDTO toResultadoDTO(ResultadoSimulado r) {
        return new ResultadoDTO(r.getTotalQuestoes(), r.getAcertos(), r.getErros(), r.getBrancos(),
                r.getPercentualAcerto(), r.isAprovado(), r.getTempoGastoSegundos());
    }
}