package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.TutorIaService;
import com.exame.ordem.tutor.TutorQuestaoNaoEncontradaException;
import com.exame.ordem.tutor.TutorAcessoNegadoException;
import com.exame.ordem.tutor.config.RagProperties;
import com.exame.ordem.tutor.config.TutorProperties;
import com.exame.ordem.tutor.dto.UsoTutorResumo;
import com.exame.ordem.tutor.internal.AlternativaTutorDTO;
import com.exame.ordem.tutor.internal.FonteRag;
import com.exame.ordem.tutor.internal.TutorQuestaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Orquestra uma conversa do tutor. Ordem de proteção (do mais barato ao mais caro):
 * acesso do plano, existência da questão (cache), admissão de sessão (global e por usuário),
 * reserva atômica de cota. Só então abre o SSE e dispara o fluxo.
 */
@Service
public class TutorIaServiceImpl implements TutorIaService {

    private static final Logger log = LoggerFactory.getLogger(TutorIaServiceImpl.class);

    /** Estimativa grosseira de tokens do prompt (português ~3 caracteres por token). */
    private static final int CARACTERES_POR_TOKEN = 3;

    private final RagProviderResolver ragResolver;
    private final CotaTutorTransacionalService cotaService;
    private final TutorCotaSeguro cotaSeguro;
    private final LimitesPolicy limitesPolicy;
    private final SseEventWriter sseEventWriter;
    private final TutorQuestaoRepository questaoRepository;
    private final TutorPromptFormatter formatter;
    private final TutorCacheFacade cache;
    private final TutorStreamingLlm streamingLlm;
    private final ProvedoresLlm provedoresLlm;
    private final AdmissaoSessoes admissao;
    private final RagProperties ragProps;
    private final long sseTimeoutMillis;
    private final Clock clock;

    // Package-private de propósito: recebe tipos package-private (o Spring injeta por reflexão).
    TutorIaServiceImpl(
            RagProviderResolver ragResolver,
            CotaTutorTransacionalService cotaService,
            TutorCotaSeguro cotaSeguro,
            LimitesPolicy limitesPolicy,
            SseEventWriter sseEventWriter,
            TutorQuestaoRepository questaoRepository,
            TutorPromptFormatter formatter,
            TutorCacheFacade cache,
            TutorStreamingLlm streamingLlm,
            ProvedoresLlm provedoresLlm,
            AdmissaoSessoes admissao,
            RagProperties ragProps,
            TutorProperties props,
            Clock clock
    ) {
        this.ragResolver = ragResolver;
        this.cotaService = cotaService;
        this.cotaSeguro = cotaSeguro;
        this.limitesPolicy = limitesPolicy;
        this.sseEventWriter = sseEventWriter;
        this.questaoRepository = questaoRepository;
        this.formatter = formatter;
        this.cache = cache;
        this.streamingLlm = streamingLlm;
        this.provedoresLlm = provedoresLlm;
        this.admissao = admissao;
        this.ragProps = ragProps;
        this.sseTimeoutMillis = props.sse().timeout().toMillis();
        this.clock = clock;
    }

    @Override
    public SseEmitter conversar(Long usuarioId, Integer questaoId, String mensagem) {
        LocalDate hoje = LocalDate.now(clock);

        // 1) Plano sem acesso: falha antes de tocar no banco de cota.
        var limites = limitesPolicy.para(usuarioId);
        if (limites.mensagens() <= 0 || limites.tokens() <= 0) {
            throw new TutorAcessoNegadoException("O tutor não está disponível no seu plano.");
        }

        // 2) Questão inexistente (leitura em cache): não gasta reserva nem sessão.
        if (questaoRepository.contextoDaQuestao(questaoId).isEmpty()) {
            throw new TutorQuestaoNaoEncontradaException("Questão não encontrada.");
        }

        // 3) Admissão: capacidade global e streams simultâneos por usuário.
        var permissao = admissao.adquirir(usuarioId);

        // 4) Reserva atômica da cota; se falhar, devolve a vaga de sessão.
        try {
            cotaService.reservar(usuarioId, hoje, limites.mensagens(), limites.tokens());
        } catch (RuntimeException e) {
            permissao.liberar();
            throw e;
        }

        var emitter = new SseEmitter(sseTimeoutMillis);
        var sessao = new SessaoSse(emitter);
        var cota = new CotaSessao(usuarioId, hoje, cotaSeguro, provedoresLlm.maxTokensResposta());

        // liberar() é idempotente: completion, error e timeout podem se sobrepor.
        emitter.onCompletion(() -> {
            sessao.marcarEncerrada();
            permissao.liberar();
        });
        emitter.onError(ex -> {
            sessao.marcarEncerrada();
            cota.encerrarComEstornoSeNecessario();
            permissao.liberar();
        });
        emitter.onTimeout(() -> {
            sessao.marcarEncerrada();
            cota.encerrarComEstornoSeNecessario();
            sessao.completarPorTimeout();
            permissao.liberar();
        });

        Thread.ofVirtual()
                .name("tutor-sse-" + usuarioId + "-q" + questaoId)
                .start(() -> executarFluxoTutor(sessao, cota, questaoId, mensagem));

        return emitter;
    }

    private void executarFluxoTutor(SessaoSse sessao, CotaSessao cota,
                                    Integer questaoId, String mensagem) {
        try {
            var ctxOpt = questaoRepository.contextoDaQuestao(questaoId);
            if (ctxOpt.isEmpty()) {
                cota.estornarUmaVez();
                avisoFinal(sessao, "Questão não encontrada no banco.");
                return;
            }
            var ctx = ctxOpt.get();
            var alternativas = aplicarModeracao(questaoRepository.alternativasDaQuestao(questaoId));

            if (sessao.isEncerrada()) {
                cota.estornarUmaVez();
                return;
            }
            sessao.enviarEvento("resposta", formatter.montarRespostaBanco(ctx, alternativas));

            String chave = formatter.gerarChaveCache(ctx, alternativas, mensagem);
            var emCache = cache.buscar(chave, mensagem, questaoId);
            if (emCache.isPresent()) {
                cota.estornarUmaVez();
                sessao.comEmitter(em -> sseEventWriter.enviarMeta(em, List.of()));
                sessao.enviarEvento("delta", emCache.get());
                sessao.concluirComSucesso();
                return;
            }

            var fontes = buscarFontesSeguro(cota.usuarioId(), questaoId, mensagem);
            if (sessao.isEncerrada()) {
                cota.estornarUmaVez();
                return;
            }
            sessao.comEmitter(em -> sseEventWriter.enviarMeta(em, fontes));

            String prompt = formatter.montarPromptLlm(ctx, alternativas, fontes, mensagem);
            cota.definirEntradaEstimada(prompt.length() / CARACTERES_POR_TOKEN);
            streamingLlm.iniciar(sessao, cota, prompt, chave, questaoId, mensagem);

        } catch (Exception e) {
            log.error("Erro inesperado no fluxo do tutor (usuario={}, questao={})",
                    cota.usuarioId(), questaoId, e);
            cota.estornarUmaVez();
            avisoFinal(sessao, "Ocorreu um erro ao carregar os dados da questão.");
        }
    }

    /**
     * Mesma regra de moderação do RAG, aplicada aos comentários da própria questão: comentário
     * com status bloqueado (ex.: ERRADO, SUSPEITO) não é exibido nem enviado ao LLM.
     */
    private List<AlternativaTutorDTO> aplicarModeracao(List<AlternativaTutorDTO> alternativas) {
        return alternativas.stream()
                .map(a -> a.possuiComentario() && ragProps.isStatusBloqueado(a.comentarioStatus())
                        ? a.semComentario()
                        : a)
                .toList();
    }

    private List<FonteRag> buscarFontesSeguro(Long usuarioId, Integer questaoId, String mensagem) {
        try {
            return ragResolver.buscar(mensagem, questaoId);
        } catch (Exception e) {
            log.warn("RAG indisponível (usuario={}, questao={}): {}", usuarioId, questaoId, e.getMessage());
            return List.of();
        }
    }

    private void avisoFinal(SessaoSse sessao, String msg) {
        sessao.enviarEventoSilencioso("resposta", "⚠️ " + msg);
        sessao.concluirComSucesso();
    }

    @Override
    public UsoTutorResumo consultarUso(Long usuarioId) {
        LocalDate hoje = LocalDate.now(clock);
        var limites = limitesPolicy.para(usuarioId);
        var uso = cotaService.uso(usuarioId, hoje);

        return new UsoTutorResumo(
                uso.mensagens(),
                uso.tokens(),
                limites.mensagens(),
                limites.tokens(),
                Math.max(0, limites.mensagens() - uso.mensagens())
        );
    }
}