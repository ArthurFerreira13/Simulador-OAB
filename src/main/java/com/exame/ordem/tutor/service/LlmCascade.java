package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.LlmProvider;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Primary
class LlmCascade implements LlmProvider, StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(LlmCascade.class);

    /**
     * Latência sentinela para provedores que nunca responderam ao ping.
     */
    private static final long LATENCIA_PADRAO_SEM_PING = 9999L;

    /**
     * Penalidade aplicada a provedores locais (freellmapi, ollama).
     * O TCP Connect deles é rápido (1-2ms), mas a geração real pode ser lenta
     * porque o proxy escolhe modelos internamente sem respeitar nossa ordenação.
     */
    private static final long PENALIDADE_PROVEDOR_LOCAL_MS = 5000L;

    private final List<LlmProvider> provedores;
    private final CircuitBreakerRegistry breakers;
    private final MeterRegistry meters;
    private final ScheduledThreadPoolExecutor vigia;
    private final ProvedorSaude provedorSaude;

    LlmCascade(ProvedoresLlm provedoresLlm,
               CircuitBreakerRegistry breakers,
               MeterRegistry meters,
               ProvedorSaude provedorSaude) {
        this.provedores = provedoresLlm.lista();
        this.breakers = breakers;
        this.meters = meters;
        this.provedorSaude = provedorSaude;

        this.vigia = new ScheduledThreadPoolExecutor(1, tarefa -> {
            Thread t = new Thread(tarefa, "llm-ttft-vigia");
            t.setDaemon(true);
            return t;
        });
        this.vigia.setRemoveOnCancelPolicy(true);

        log.info("Cascata do tutor carregada. Ordem inicial: {}",
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
        List<LlmProvider> ordenados = ordenarPorLatencia();
        tentar(ordenados, 0, request, downstream);
    }

    /**
     * Ordena os provedores pela prioridade efetiva (latência + penalidade se local).
     *
     * <p>Provedores locais (freellmapi, ollama) recebem +5000ms de penalidade porque
     * o ping TCP não reflete a latência real de geração — o proxy interno deles pode
     * escolher modelos lentos. Provedores de nuvem mantêm a latência real do ping.
     *
     * @return lista imutável ordenada por prioridade crescente
     */
    private List<LlmProvider> ordenarPorLatencia() {
        var lista = new ArrayList<>(provedores);

        long provedoresComLatencia = provedores.stream()
                .filter(p -> provedorSaude.latencia(p.name()) < LATENCIA_PADRAO_SEM_PING)
                .count();

        if (provedoresComLatencia == 0) {
            log.warn("⚠️ Nenhum provedor com latência registrada. Ping inicial ainda em andamento. " +
                            "Usando ordem original do YAML: {}",
                    provedores.stream().map(LlmProvider::name).toList());
            return List.copyOf(lista);
        }

        lista.sort(Comparator
                .comparingLong(this::prioridadeEfetiva)
                .thenComparing(LlmProvider::name));

        if (log.isInfoEnabled()) {
            String ordemFinal = lista.stream()
                    .map(p -> String.format("%s(%dms)", p.name(), prioridadeEfetiva(p)))
                    .collect(Collectors.joining(", "));
            log.info("Cascata ordenada por prioridade: [{}]", ordemFinal);
        }

        return List.copyOf(lista);
    }

    /**
     * Prioridade efetiva = latência do ping + penalidade para provedores locais.
     */
    private long prioridadeEfetiva(LlmProvider p) {
        long latencia = provedorSaude.latencia(p.name());

        if ("freellmapi".equals(p.name()) || "ollama".equals(p.name())) {
            return latencia + PENALIDADE_PROVEDOR_LOCAL_MS;
        }

        return latencia;
    }

    private void tentar(List<LlmProvider> lista, int indice, ChatRequest request, StreamingChatResponseHandler downstream) {
        if (indice >= lista.size()) {
            log.error("Todos os provedores LLM falharam ou estão indisponíveis");
            meters.counter("tutor.llm.cascata_esgotada").increment();
            downstream.onError(new IllegalStateException("Nenhum provedor de LLM disponível no momento."));
            return;
        }

        LlmProvider provedor = lista.get(indice);
        CircuitBreaker circuito = breakers.circuitBreaker(provedor.name());

        ProvedorSaude.Estado saude = provedorSaude.verificar(provedor.name());
        if (saude != ProvedorSaude.Estado.OK) {
            log.warn("Provedor {} pulado. Motivo: {}", provedor.name(), saude.motivo());
            meters.counter("tutor.llm.pulados_saude", "provider", provedor.name()).increment();
            tentar(lista, indice + 1, request, downstream);
            return;
        }

        if (!circuito.tryAcquirePermission()) {
            log.warn("Circuito aberto para {}; pulando", provedor.name());
            meters.counter("tutor.llm.pulados", "provider", provedor.name()).increment();
            tentar(lista, indice + 1, request, downstream);
            return;
        }

        Tentativa t = new Tentativa();
        long ttftMs = provedor.ttftTimeout().toMillis();

        t.vigia = vigia.schedule(
                () -> estourouTtft(lista, indice, provedor, circuito, t, request, downstream),
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
                        return;
                    }
                    downstream.onPartialResponse(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    boolean venceu = t.estado.compareAndSet(Tentativa.STREAMING, Tentativa.ENCERRADA)
                            || t.estado.compareAndSet(Tentativa.AGUARDANDO, Tentativa.ENCERRADA);
                    if (!venceu) return;

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
                    tratarErro(lista, indice, provedor, circuito, t, request, downstream, erro);
                }
            });
        } catch (Exception erro) {
            tratarErro(lista, indice, provedor, circuito, t, request, downstream, erro);
        }
    }

    private void estourouTtft(List<LlmProvider> lista, int indice, LlmProvider provedor, CircuitBreaker circuito,
                              Tentativa t, ChatRequest request, StreamingChatResponseHandler downstream) {
        if (!t.estado.compareAndSet(Tentativa.AGUARDANDO, Tentativa.ABANDONADA)) return;

        long duracaoNs = System.nanoTime() - t.inicioNs;
        circuito.onError(duracaoNs, TimeUnit.NANOSECONDS,
                new TimeoutException("Sem primeiro fragmento em " + ms(duracaoNs) + " ms"));
        meters.counter("tutor.llm.ttft_timeout", "provider", provedor.name()).increment();
        meters.counter("tutor.llm.erros", "provider", provedor.name()).increment();

        provedorSaude.agendarCooldown(provedor.name(), Duration.ofSeconds(10));

        log.warn("LLM provider={} sem primeiro fragmento após {} ms; tentando o próximo",
                provedor.name(), ms(duracaoNs));
        tentar(lista, indice + 1, request, downstream);
    }

    private void tratarErro(List<LlmProvider> lista, int indice, LlmProvider provedor, CircuitBreaker circuito,
                            Tentativa t, ChatRequest request, StreamingChatResponseHandler downstream, Throwable erro) {
        boolean antes = t.estado.compareAndSet(Tentativa.AGUARDANDO, Tentativa.ABANDONADA);
        boolean depois = !antes && t.estado.compareAndSet(Tentativa.STREAMING, Tentativa.ENCERRADA);
        if (!antes && !depois) return;

        t.cancelarVigia();
        long duracaoNs = System.nanoTime() - t.inicioNs;
        circuito.onError(duracaoNs, TimeUnit.NANOSECONDS, erro);
        meters.counter("tutor.llm.erros", "provider", provedor.name()).increment();

        Duration cooldown = Duration.ofSeconds(5);
        if (erro instanceof RateLimitException) {
            cooldown = Duration.ofSeconds(45);
            log.warn("RateLimit (429) no provedor {}. Cooldown de {}s.", provedor.name(), cooldown.getSeconds());
        } else if (erro instanceof InternalServerException) {
            cooldown = Duration.ofSeconds(15);
            log.warn("InternalServerError (503) no provedor {}. Cooldown de {}s.", provedor.name(), cooldown.getSeconds());
        } else {
            log.warn("Erro no provedor {}. Cooldown de {}s. Erro: {}", provedor.name(), cooldown.getSeconds(), erro.getMessage());
        }
        provedorSaude.agendarCooldown(provedor.name(), cooldown);

        if (antes) {
            log.warn("LLM provider={} falhou antes do primeiro fragmento ({} ms); tentando o próximo",
                    provedor.name(), ms(duracaoNs));
            tentar(lista, indice + 1, request, downstream);
            return;
        }
        log.error("LLM provider={} falhou no meio da resposta ({} ms)", provedor.name(), ms(duracaoNs), erro);
        downstream.onError(erro);
    }

    private void registrarTokens(LlmProvider provedor, ChatResponse response) {
        if (response == null || response.tokenUsage() == null) return;
        var uso = response.tokenUsage();
        if (uso.inputTokenCount() != null)
            meters.counter("tutor.llm.tokens", "provider", provedor.name(), "tipo", "entrada").increment(uso.inputTokenCount());
        if (uso.outputTokenCount() != null)
            meters.counter("tutor.llm.tokens", "provider", provedor.name(), "tipo", "saida").increment(uso.outputTokenCount());
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
            if (v != null) v.cancel(false);
        }
    }
}