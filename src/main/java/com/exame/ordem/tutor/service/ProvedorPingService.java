package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.CascataProperties;
import com.exame.ordem.tutor.config.TutorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Health-check periódico dos provedores de LLM <b>locais</b> (freellmapi, ollama).
 *
 * <p><b>Provedores remotos não são pingados.</b> Cada GET em {@code /models} conta como
 * chamada na cota do provedor (o trial do Cohere, por exemplo, tem 1000 chamadas/mês e
 * um ping a cada 30 s por chave o esgotava em poucas horas). A saúde dos remotos vem do
 * tráfego real: circuit breaker + cooldown aplicado pelo {@link LlmRouter} nos erros.
 *
 * <p><b>Sanitização de logs:</b> exceções de provedores podem conter
 * fragmentos de chave em {@code e.getMessage()} (ex.: mensagens de erro
 * que ecoam o {@code Authorization} recebido). {@link #sanitizarErro}
 * redige padrões conhecidos ({@code sk-...}, {@code AIza...},
 * {@code Bearer ...}, {@code api_key=...}) e trunca a mensagem antes de
 * logar, evitando persistir segredos em arquivo de log.
 */
@Service
public class ProvedorPingService {

    private static final Logger log = LoggerFactory.getLogger(ProvedorPingService.class);
    private static final int SOCKET_TIMEOUT_MS = 1_000;

    private final List<CascataProperties.Provedor> provedores;
    private final ProvedorSaude provedorSaude;
    private final TutorProperties.OllamaChat ollamaCfg;

    public ProvedorPingService(
            CascataProperties cascataProps,
            TutorProperties tutorProps,
            ProvedorSaude provedorSaude) {
        this.provedores = cascataProps.provedores();
        this.provedorSaude = provedorSaude;
        this.ollamaCfg = tutorProps.ollamaChat();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void pingarNoBoot() {
        log.info("[PING] Aplicação pronta. Disparando verificação inicial de provedores...");
        Thread.ofVirtual().name("ping-boot").start(this::pingarTodos);
    }

    @Scheduled(fixedDelay = 10_000)
    public void pingarTodos() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            provedores.stream()
                    .filter(CascataProperties.Provedor::habilitado)
                    .filter(p -> isLocalhost(p.urlParaPing()))
                    .filter(p -> provedorSaude.devePingar(p.id()))
                    .forEach(p -> executor.submit(() -> pingar(p)));

            if (ollamaCfg.habilitado() && provedorSaude.devePingar("ollama")) {
                executor.submit(this::pingarOllama);
            }
        }
    }

    private void pingar(CascataProperties.Provedor p) {
        long inicio = System.currentTimeMillis();
        try {
            pingarSocket(p.urlParaPing());
            provedorSaude.registrarSucessoPing(p.id(), System.currentTimeMillis() - inicio);
        } catch (Exception e) {
            provedorSaude.registrarFalhaPing(p.id());
            log.debug("[PING] {} falhou: {}", p.id(), sanitizarErro(e));
        }
    }

    private void pingarOllama() {
        long inicio = System.currentTimeMillis();
        try {
            pingarSocket(ollamaCfg.url());
            provedorSaude.registrarSucessoPing("ollama", System.currentTimeMillis() - inicio);
        } catch (Exception e) {
            provedorSaude.registrarFalhaPing("ollama");
            log.debug("[PING] ollama falhou: {}", sanitizarErro(e));
        }
    }

    private static boolean isLocalhost(String url) {
        return url.contains("localhost") || url.contains("127.0.0.1");
    }

    private static void pingarSocket(String url) throws Exception {
        URI uri = URI.create(url);
        int porta = uri.getPort() > 0 ? uri.getPort() : 80;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), porta), SOCKET_TIMEOUT_MS);
        }
    }

    /**
     * Converte uma exceção em mensagem segura para log: classe + mensagem
     * com padrões de chave redigidos, truncada a 200 chars. Nunca lança.
     */
    static String sanitizarErro(Throwable e) {
        if (e == null) return "erro desconhecido";
        String mensagem = e.getMessage();
        if (mensagem == null) return e.getClass().getSimpleName();
        if (mensagem.length() > 200) {
            mensagem = mensagem.substring(0, 200) + "…";
        }
        mensagem = mensagem
                .replaceAll("sk-[A-Za-z0-9_-]{10,}", "sk-***")
                .replaceAll("AIza[A-Za-z0-9_-]{20,}", "AIza***")
                .replaceAll("(?i)Bearer\\s+\\S+", "Bearer ***")
                .replaceAll("(?i)api[_-]?key=[^&\\s]+", "api_key=***")
                .replaceAll("(?i)token=[^&\\s]+", "token=***");
        return e.getClass().getSimpleName() + ": " + mensagem;
    }
}