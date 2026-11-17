package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.TutorIaService;
import com.exame.ordem.tutor.dto.UsoTutorResumo;
import com.exame.ordem.tutor.internal.FonteRag;
import com.exame.ordem.tutor.internal.TutorQuestaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class TutorIaServiceImpl implements TutorIaService {

    private static final Logger log = LoggerFactory.getLogger(TutorIaServiceImpl.class);
    private static final long SSE_TIMEOUT_MILLIS = 45_000L;

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
    private final Clock clock;

    public TutorIaServiceImpl(
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
        this.clock = clock;
    }


    @Override
    public SseEmitter conversar(Long usuarioId, Integer questaoId, String mensagem) {
        LocalDate hoje = LocalDate.now(clock);
        var limites = limitesPolicy.para(usuarioId);
        int tokensReserva = provedoresLlm.maxTokensResposta();
        cotaService.reservar(usuarioId, hoje, limites.mensagens(), limites.tokens());

        var emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        var sessao = new SessaoSse(emitter);
        var cota = new CotaSessao(usuarioId, hoje, cotaSeguro, tokensReserva);

        emitter.onCompletion(sessao::marcarEncerrada);
        emitter.onError(ex -> { sessao.marcarEncerrada(); cota.encerrarComEstornoSeNecessario(); });
        emitter.onTimeout(() -> {
            sessao.marcarEncerrada();
            cota.encerrarComEstornoSeNecessario();
            sessao.completarPorTimeout();
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
            var alternativas = questaoRepository.alternativasDaQuestao(questaoId);

            if (sessao.isEncerrada()) return;
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
            if (sessao.isEncerrada()) return;
            sessao.comEmitter(em -> sseEventWriter.enviarMeta(em, fontes));

            String prompt = formatter.montarPromptLlm(ctx, alternativas, fontes, mensagem);
            streamingLlm.iniciar(sessao, cota, prompt, chave, questaoId, mensagem);

        } catch (Exception e) {
            log.error("Erro inesperado no fluxo do tutor (usuario={}, questao={})",
                    cota.usuarioId(), questaoId, e);
            cota.estornarUmaVez();
            avisoFinal(sessao, "Ocorreu um erro ao carregar os dados da questão.");
        }
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

        int mensagensUsadas = cotaService.mensagensUsadas(usuarioId, hoje);
        long tokensUsados = cotaService.tokensUsados(usuarioId, hoje);

        int limiteDiario = limites.mensagens();
        long limiteTokens = limites.tokens();
        int restantes = Math.max(0, limiteDiario - mensagensUsadas);

        return new UsoTutorResumo(
                mensagensUsadas,
                tokensUsados,
                limiteDiario,
                limiteTokens,
                restantes
        );
    }

}