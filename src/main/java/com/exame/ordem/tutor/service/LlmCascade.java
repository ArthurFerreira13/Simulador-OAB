package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cascata de provedores de chat com failover.
 *
 * <p>O failover só ocorre ANTES do primeiro fragmento (depois, trocar misturaria duas respostas).
 * Um vigia de TTFT abandona o provedor que não emite o primeiro fragmento no prazo. O estado de
 * cada tentativa é atômico e decide quem vence a corrida entre vigia, fragmento e erro;
 * callbacks de tentativas abandonadas são descartados.
 */
@Component
@Primary
class LlmCascade implements LlmProvider, StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(LlmCascade.class);

    private final List<LlmProvider> provedores;
    private final CircuitBreakerRegistry breakers;
    private final MeterRegistry meters;
    private final ScheduledThreadPoolExecutor vigia;

    LlmCascade(ProvedoresLlm provedoresLlm, CircuitBreakerRegistry breakers, MeterRegistry meters) {
        this.provedores = provedoresLlm.lista();
        this.breakers = breakers;
        this.meters = meters;

        this.vigia = new ScheduledThreadPoolExecutor(1, tarefa -> {
            Thread t = new Thread(tarefa, "llm-ttft-vigia");
            t.setDaemon(true);
            return t;
        });
        this.vigia.setRemoveOnCancelPolicy(true);

        log.info("Cascata do tutor carregada. Ordem dos provedores: {}",
                provedores.stream().map(LlmProvider::name).toList());
    }

    @PreDestroy
    void encerrar() {
        vigia.shutdownNow();
    }

    @Override public StreamingChatModel streaming() { return this; }
    @Override public String name() { return "cascata"; }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler downstream) {
        tentar(0, request, downstream);
    }

    private void tentar(int indice, ChatRequest request, StreamingChatResponseHandler downstream) {
        if (indice >= provedores.size()) {
            log.error("Todos os provedores LLM falharam ou estão indisponíveis");
            meters.counter("tutor.llm.cascata_esgotada").increment();
            downstream.onError(new IllegalStateException("Nenhum provedor de LLM disponível"));
            return;
        }

        LlmProvider provedor = provedores.get(indice);
        CircuitBreaker circuito = breakers.circuitBreaker(provedor.name());

        if (!circuito.tryAcquirePermission()) {
            log.warn("Circuito aberto para {}; pulando", provedor.name());
            meters.counter("tutor.llm.pulados", "provider", provedor.name()).increment();
            tentar(indice + 1, request, downstream);
            return;
        }

        Tentativa t = new Tentativa();
        long ttftMs = provedor.ttftTimeout().toMillis();

        // Vigia agendado ANTES da chamada: se o chat() travar, o prazo já está correndo.
        t.vigia = vigia.schedule(
                () -> estourouTtft(indice, provedor, circuito, t, request, downstream),
                ttftMs, TimeUnit.MILLISECONDS);

        log.info("Tentando provedor LLM: {} (ttft máx {} ms)", provedor.name(), ttftMs);

        try {
            provedor.streaming().chat(request, new StreamingChatResponseHandler() {

                @Override
                public void onPartialResponse(String token) {
                    if (t.estado.compareAndSet(Tentativa.AGUARDANDO, Tentativa.STREAMING)) {
                        t.primeiroFragmentoNs = System.nanoTime();
                        t.cancelarVigia();
                        log.info("LLM provider={} primeiroFragmentoMs={}", provedor.name(),
                                ms(t.primeiroFragmentoNs - t.inicioNs));
                    } else if (t.estado.get() != Tentativa.STREAMING) {
                        return; // abandonada ou encerrada
                    }
                    downstream.onPartialResponse(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    boolean venceu = t.estado.compareAndSet(Tentativa.STREAMING, Tentativa.ENCERRADA)
                            || t.estado.compareAndSet(Tentativa.AGUARDANDO, Tentativa.ENCERRADA);
                    if (!venceu) {
                        return;
                    }
                    t.cancelarVigia();

                    long duracaoNs = System.nanoTime() - t.inicioNs;
                    circuito.onSuccess(duracaoNs, TimeUnit.NANOSECONDS);
                    meters.counter("tutor.llm.respostas", "provider", provedor.name()).increment();
                    registrarTokens(provedor, response);

                    log.info("LLM provider={} concluído; totalMs={}", provedor.name(), ms(duracaoNs));
                    downstream.onCompleteResponse(response);
                }

                @Override
                public void onError(Throwable erro) {
                    tratarErro(indice, provedor, circuito, t, request, downstream, erro);
                }
            });
        } catch (Exception erro) {
            tratarErro(indice, provedor, circuito, t, request, downstream, erro);
        }
    }

    private void estourouTtft(int indice, LlmProvider provedor, CircuitBreaker circuito, Tentativa t,
                              ChatRequest request, StreamingChatResponseHandler downstream) {
        if (!t.estado.compareAndSet(Tentativa.AGUARDANDO, Tentativa.ABANDONADA)) {
            return; // primeiro fragmento ou erro chegou antes
        }
        long duracaoNs = System.nanoTime() - t.inicioNs;
        circuito.onError(duracaoNs, TimeUnit.NANOSECONDS,
                new TimeoutException("Sem primeiro fragmento em " + ms(duracaoNs) + " ms"));
        meters.counter("tutor.llm.ttft_timeout", "provider", provedor.name()).increment();
        meters.counter("tutor.llm.erros", "provider", provedor.name()).increment();

        log.warn("LLM provider={} sem primeiro fragmento após {} ms; tentando o próximo",
                provedor.name(), ms(duracaoNs));
        tentar(indice + 1, request, downstream);
    }

    private void tratarErro(int indice, LlmProvider provedor, CircuitBreaker circuito, Tentativa t,
                            ChatRequest request, StreamingChatResponseHandler downstream, Throwable erro) {
        boolean antes = t.estado.compareAndSet(Tentativa.AGUARDANDO, Tentativa.ABANDONADA);
        boolean depois = !antes && t.estado.compareAndSet(Tentativa.STREAMING, Tentativa.ENCERRADA);
        if (!antes && !depois) {
            return; // já abandonada pelo vigia ou encerrada
        }

        t.cancelarVigia();
        long duracaoNs = System.nanoTime() - t.inicioNs;
        circuito.onError(duracaoNs, TimeUnit.NANOSECONDS, erro);
        meters.counter("tutor.llm.erros", "provider", provedor.name()).increment();

        if (antes) {
            log.warn("LLM provider={} falhou antes do primeiro fragmento ({} ms); tentando o próximo: {}",
                    provedor.name(), ms(duracaoNs), erro.toString());
            tentar(indice + 1, request, downstream);
            return;
        }
        log.error("LLM provider={} falhou no meio da resposta ({} ms)", provedor.name(), ms(duracaoNs), erro);
        downstream.onError(erro);
    }

    private void registrarTokens(LlmProvider provedor, ChatResponse response) {
        if (response == null || response.tokenUsage() == null) {
            return;
        }
        var uso = response.tokenUsage();
        if (uso.inputTokenCount() != null) {
            meters.counter("tutor.llm.tokens", "provider", provedor.name(), "tipo", "entrada")
                    .increment(uso.inputTokenCount());
        }
        if (uso.outputTokenCount() != null) {
            meters.counter("tutor.llm.tokens", "provider", provedor.name(), "tipo", "saida")
                    .increment(uso.outputTokenCount());
        }
    }

    private static long ms(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private static final class Tentativa {
        static final int AGUARDANDO = 0, STREAMING = 1, ENCERRADA = 2, ABANDONADA = 3;

        final AtomicInteger estado = new AtomicInteger(AGUARDANDO);
        final long inicioNs = System.nanoTime();
        volatile long primeiroFragmentoNs;
        volatile ScheduledFuture<?> vigia;

        void cancelarVigia() {
            ScheduledFuture<?> v = vigia;
            if (v != null) {
                v.cancel(false);
            }
        }
    }
}