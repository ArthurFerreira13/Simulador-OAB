package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import com.exame.ordem.tutor.config.CascataProperties;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Roteador de LLM no padrão hedge/race.
 *
 * <p><b>Ordenação por TTFT configurado:</b> a lista de provedores é
 * ordenada pelo TTFT de {@link CascataProperties.Provedor#ttft()}, com
 * o ping observado ({@link ProvedorSaude#latencia(String)}) como
 * desempate. NÃO usar ping como chave primária: provedores em localhost
 * (Ollama, FreeLLMAPI) respondem ping em ~2 ms, mas geram o primeiro
 * token em 15–30 s. Provedor sem TTFT no {@code CascataProperties} cai
 * no fim da lista ({@code Long.MAX_VALUE}).
 *
 * <p><b>Hedge:</b> dispara o primeiro da lista; se ele não emitir token
 * em {@code hedgeDelay} (default 4.5 s), dispara o segundo em paralelo.
 * O primeiro que emitir token vence. O perdedor não é cancelável — a
 * API do LangChain4j 1.20 ({@code StreamingChatModel.chat(request, handler)}
 * → void) não expõe handle de cancelamento, então seus tokens são
 * simplesmente descartados.
 *
 * <p><b>Vencedor por índice:</b> o vencedor é identificado pelo índice do
 * provedor na lista. Só ele repassa tokens, conclusão e erro ao downstream;
 * os demais são ignorados depois da definição do vencedor.
 *
 * <p><b>Erros acionam cooldown:</b> falhas classificáveis (429, 402,
 * modelo descontinuado, cota diária) colocam o provedor em cooldown no
 * {@link ProvedorSaude}, para que as próximas requisições não tentem de
 * novo um provedor sabidamente fora do ar.
 *
 * <p><b>Limite local de RPM:</b> o {@code rpm} configurado por provedor é
 * aplicado com um bucket local antes do disparo (0 = sem limite).
 *
 * <p><b>Prazo do primeiro token:</b> se nenhum provedor emitir token em
 * {@link #PRAZO_PRIMEIRO_TOKEN_MS}, o downstream recebe erro, em vez de
 * ficar pendurado até o timeout do SSE.
 */
@Component("llmRouter")
@Primary
public class LlmRouter implements StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    private static final long PRAZO_PRIMEIRO_TOKEN_MS = 35_000L;
    private static final int SEM_VENCEDOR = -1;

    private final List<LlmProvider> provedores;
    private final ProvedorSaude saude;
    private final LlmCooldownPolicy cooldownPolicy;
    private final CircuitBreakerRegistry circuitBreakers;
    private final BulkheadRegistry bulkheads;
    private final ScheduledExecutorService scheduler;
    private final long hedgeDelayMs;
    private final Map<String, Long> ttftPorProvedor;
    private final Map<String, Bucket> limitesRpm;

    LlmRouter(ProvedoresLlm provedoresLlm,
              ProvedorSaude saude,
              LlmCooldownPolicy cooldownPolicy,
              CircuitBreakerRegistry circuitBreakers,
              BulkheadRegistry bulkheads,
              CascataProperties cascataProps) {
        this.provedores = provedoresLlm.getProvedores();
        this.saude = saude;
        this.cooldownPolicy = cooldownPolicy;
        this.circuitBreakers = circuitBreakers;
        this.bulkheads = bulkheads;
        this.hedgeDelayMs = cascataProps.hedgeDelay().toMillis();
        this.ttftPorProvedor = cascataProps.provedores().stream()
                .collect(Collectors.toMap(
                        CascataProperties.Provedor::id,
                        p -> p.ttft().toMillis(),
                        (a, b) -> a));

        Map<String, Bucket> buckets = new HashMap<>();
        for (CascataProperties.Provedor p : cascataProps.provedores()) {
            if (p.rpm() > 0) {
                buckets.put(p.id(), Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(p.rpm())
                                .refillGreedy(p.rpm(), Duration.ofMinutes(1))
                                .build())
                        .build());
            }
        }
        this.limitesRpm = Map.copyOf(buckets);

        // Só marca o tempo. Tudo o que pode bloquear (estorno em banco, envio SSE, abertura de
        // conexão do hedge) roda em thread virtual própria; senão uma operação lenta atrasaria
        // todos os timers de hedge e de prazo do primeiro token.
        var agendador = new ScheduledThreadPoolExecutor(1, Thread.ofVirtual().name("llm-hedge-", 0).factory());
        agendador.setRemoveOnCancelPolicy(true); // timer cancelado sai da fila na hora
        this.scheduler = agendador;
    }

    private static void emThreadVirtual(Runnable acao) {
        Thread.ofVirtual().name("llm-timer-task").start(acao);
    }

    @PreDestroy
    void encerrar() {
        scheduler.shutdownNow();
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler downstream) {
        List<LlmProvider> disponiveis = provedores.stream()
                .filter(p -> saude.verificar(p.name()) == ProvedorSaude.Estado.OK)
                .filter(p -> circuitBreakers.circuitBreaker(p.name()).getState() != CircuitBreaker.State.OPEN)
                .sorted(Comparator
                        .comparingLong((LlmProvider p) ->
                                ttftPorProvedor.getOrDefault(p.name(), p.ttftMs()))
                        .thenComparingLong(p -> saude.latencia(p.name())))
                .toList();

        if (disponiveis.isEmpty()) {
            downstream.onError(new IllegalStateException("Nenhum provedor de IA disponível no momento."));
            return;
        }

        var execucao = new ExecucaoCascata(disponiveis, request, downstream);
        execucao.iniciar();
    }

    private final class ExecucaoCascata {
        private final List<LlmProvider> disponiveis;
        private final ChatRequest request;
        private final StreamingChatResponseHandler downstream;
        private final AtomicInteger vencedor = new AtomicInteger(SEM_VENCEDOR);
        private final AtomicBoolean finalizado = new AtomicBoolean(false);
        private final AtomicInteger falhas = new AtomicInteger(0);
        private final AtomicBoolean[] iniciados;
        private volatile ScheduledFuture<?> prazoPrimeiroToken;

        private ExecucaoCascata(List<LlmProvider> disponiveis,
                                ChatRequest request,
                                StreamingChatResponseHandler downstream) {
            this.disponiveis = disponiveis;
            this.request = request;
            this.downstream = downstream;
            this.iniciados = new AtomicBoolean[disponiveis.size()];
            for (int i = 0; i < iniciados.length; i++) {
                iniciados[i] = new AtomicBoolean(false);
            }
        }

        void iniciar() {
            prazoPrimeiroToken = scheduler.schedule(() -> emThreadVirtual(() -> {
                if (vencedor.get() == SEM_VENCEDOR && finalizado.compareAndSet(false, true)) {
                    log.warn("[LLM Router] Nenhum token em {}ms. Encerrando com erro.", PRAZO_PRIMEIRO_TOKEN_MS);
                    downstream.onError(new IllegalStateException(
                            "Nenhum provedor de IA respondeu no prazo."));
                }
            }), PRAZO_PRIMEIRO_TOKEN_MS, TimeUnit.MILLISECONDS);

            disparar(0);
        }

        private boolean encerrada() {
            return finalizado.get();
        }

        private boolean temVencedor() {
            return vencedor.get() != SEM_VENCEDOR;
        }

        private void cancelarPrazo() {
            ScheduledFuture<?> f = prazoPrimeiroToken;
            if (f != null) {
                f.cancel(false);
            }
        }

        void disparar(int index) {
            if (index >= disponiveis.size() || temVencedor() || encerrada()) {
                return;
            }
            if (!iniciados[index].compareAndSet(false, true)) {
                return;
            }

            LlmProvider atual = disponiveis.get(index);

            // Cooldown pode ter sido agendado por outra requisição depois do filtro inicial.
            if (saude.verificar(atual.name()) != ProvedorSaude.Estado.OK) {
                registrarFalhaEAvancar(index, atual.name(), new IllegalStateException("Provedor em cooldown"));
                return;
            }

            Bucket limite = limitesRpm.get(atual.name());
            if (limite != null && !limite.tryConsume(1)) {
                registrarFalhaEAvancar(index, atual.name(), new IllegalStateException("Limite RPM local atingido"));
                return;
            }

            CircuitBreaker cb = circuitBreakers.circuitBreaker(atual.name());
            Bulkhead bh = bulkheads.bulkhead(atual.name());

            if (!cb.tryAcquirePermission()) {
                registrarFalhaEAvancar(index, atual.name(), new IllegalStateException("CircuitBreaker OPEN"));
                return;
            }
            if (!bh.tryAcquirePermission()) {
                cb.releasePermission();
                registrarFalhaEAvancar(index, atual.name(), new IllegalStateException("Bulkhead cheio"));
                return;
            }

            long inicioNanos = System.nanoTime();
            log.debug("[LLM Router] Disparando: {}", atual.name());

            if (index == 0 && disponiveis.size() > 1) {
                scheduler.schedule(() -> emThreadVirtual(() -> {
                    if (!temVencedor() && !encerrada()) {
                        log.info("[LLM Router] Hedge acionado após {}ms para reserva.", hedgeDelayMs);
                        disparar(1);
                    }
                }), hedgeDelayMs, TimeUnit.MILLISECONDS);
            }

            try {
                atual.streaming().chat(request, new StreamingChatResponseHandler() {
                    private final AtomicBoolean permissoesLiberadas = new AtomicBoolean(false);

                    private void liberarSucesso() {
                        if (permissoesLiberadas.compareAndSet(false, true)) {
                            bh.onComplete();
                            cb.onSuccess(System.nanoTime() - inicioNanos, TimeUnit.NANOSECONDS);
                        }
                    }

                    private void liberarErro(Throwable error) {
                        if (permissoesLiberadas.compareAndSet(false, true)) {
                            bh.onComplete();
                            cb.onError(System.nanoTime() - inicioNanos, TimeUnit.NANOSECONDS, error);
                        }
                    }

                    private boolean souVencedor() {
                        return vencedor.get() == index;
                    }

                    @Override
                    public void onPartialResponse(String token) {
                        if (vencedor.compareAndSet(SEM_VENCEDOR, index)) {
                            log.debug("[LLM Router] Vencedor: {}", atual.name());
                            cancelarPrazo();
                        }
                        if (souVencedor() && !encerrada()) {
                            downstream.onPartialResponse(token);
                        }
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        liberarSucesso();
                        // Resposta completa sem nenhum token parcial também conta como vitória.
                        if (vencedor.compareAndSet(SEM_VENCEDOR, index)) {
                            cancelarPrazo();
                        }
                        if (souVencedor() && finalizado.compareAndSet(false, true)) {
                            downstream.onCompleteResponse(response);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        liberarErro(error);

                        if (souVencedor()) {
                            // O vencedor caiu no meio do stream: precisa avisar o downstream,
                            // senão a requisição fica pendurada até o timeout do SSE.
                            log.warn("[LLM Router] {} (vencedor) falhou durante o stream: {}",
                                    atual.name(), error.getMessage());
                            cooldownPolicy.aplicar(atual.name(), error);
                            if (finalizado.compareAndSet(false, true)) {
                                downstream.onError(error);
                            }
                            return;
                        }

                        cooldownPolicy.aplicar(atual.name(), error);
                        registrarFalhaEAvancar(index, atual.name(), error);
                    }
                });
            } catch (Exception e) {
                bh.onComplete();
                cb.onError(System.nanoTime() - inicioNanos, TimeUnit.NANOSECONDS, e);
                cooldownPolicy.aplicar(atual.name(), e);
                registrarFalhaEAvancar(index, atual.name(), e);
            }
        }

        private void registrarFalhaEAvancar(int index, String nomeProvedor, Throwable error) {
            log.warn("[LLM Router] {} falhou: {}", nomeProvedor, error.getMessage());
            if (temVencedor() || encerrada()) {
                return;
            }
            if (falhas.incrementAndGet() >= disponiveis.size()) {
                if (finalizado.compareAndSet(false, true)) {
                    cancelarPrazo();
                    downstream.onError(new RuntimeException("Todos os provedores de IA falharam.", error));
                }
            } else {
                disparar(index + 1);
            }
        }
    }
}