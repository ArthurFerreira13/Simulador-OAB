package com.exame.ordem.tutor.service;

import com.exame.ordem.tutor.config.CascataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ProvedorPingService {

    private static final Logger log = LoggerFactory.getLogger(ProvedorPingService.class);

    private final List<CascataProperties.Provedor> provedores;
    private final ProvedorSaude provedorSaude;
    private final boolean ollamaHabilitado;
    private final String ollamaUrl;

    public ProvedorPingService(
            CascataProperties props,
            ProvedorSaude provedorSaude,
            @Value("${app.tutor.ollama-chat.habilitado:false}") boolean ollamaHabilitado,
            @Value("${app.tutor.ollama-chat.url:http://localhost:11434}") String ollamaUrl) {
        this.provedores = props.provedores();
        this.provedorSaude = provedorSaude;
        this.ollamaHabilitado = ollamaHabilitado;
        this.ollamaUrl = ollamaUrl;
    }

    /**
     * [NOVO] Dispara o ping imediatamente quando a aplicação termina de subir.
     * Não espera os 30 segundos do @Scheduled.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void pingarNoBoot() {
        log.info("🚀 [PING] Aplicação pronta. Disparando ping inicial em background...");
        // Roda em uma thread separada para não bloquear a subida da aplicação
        CompletableFuture.runAsync(this::pingarTodos);
    }

    /**
     * [AJUSTADO] Removeu o initialDelay para garantir que rode sempre no segundo 0.
     * Roda a cada 30 segundos para manter as latências atualizadas.
     */
    @Scheduled(fixedRate = 30_000, initialDelay = 0)
    public void pingarTodos() {
        var futures = provedores.stream()
                .filter(CascataProperties.Provedor::habilitado)
                .map(p -> CompletableFuture.runAsync(() -> pingar(p)))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (ollamaHabilitado) {
            pingarOllama();
        }
    }

    private void pingar(CascataProperties.Provedor p) {
        long inicio = System.currentTimeMillis();
        try {
            String url = p.urlParaPing();

            if (url.contains("localhost") || url.contains("127.0.0.1")) {
                URI uri = URI.create(url);
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), 1000);
                }
            } else {
                int timeoutMs = 3000;
                var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(timeoutMs);
                factory.setReadTimeout(timeoutMs);

                var restClient = RestClient.builder().requestFactory(factory).build();
                var spec = restClient.get().uri(url);

                if (p.headers().containsKey("Authorization")) {
                    spec.header("Authorization", p.headers().get("Authorization"));
                } else {
                    spec.header("Authorization", "Bearer " + p.apiKey());
                }
                spec.retrieve().toBodilessEntity();
            }

            long latencia = System.currentTimeMillis() - inicio;
            provedorSaude.registrarSucessoPing(p.id(), latencia);
        } catch (Exception e) {
            provedorSaude.registrarFalhaPing(p.id());
            log.debug("[PING] {} falhou: {}", p.id(), e.getMessage());
        }
    }

    private void pingarOllama() {
        long inicio = System.currentTimeMillis();
        try {
            URI uri = URI.create(ollamaUrl);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), 1000);
            }
            long latencia = System.currentTimeMillis() - inicio;
            provedorSaude.registrarSucessoPing("ollama", latencia);
        } catch (Exception e) {
            provedorSaude.registrarFalhaPing("ollama");
            log.debug("[PING] ollama falhou: {}", e.getMessage());
        }
    }
}