package com.exame.ordem.tutor.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sobe o proxy FreeLLMAPI (Node.js) em desenvolvimento.
 *
 * <p><b>Quando roda:</b> apenas em {@code @Profile("dev")} e com
 * {@code app.tutor.freellmapi.autostart=true}. O {@code ApplicationReadyEvent}
 * é usado em vez de {@code ApplicationStartedEvent} porque o proxy não é
 * pré-requisito para o contexto subir — o {@code LlmRouter} já trata sua
 * ausência com fallback para os demais provedores.
 *
 * <p><b>Assíncrono:</b> o método {@link #iniciar()} dispara um thread virtual
 * para não bloquear a thread de inicialização do Spring, seguindo o mesmo
 * padrão do {@code ProvedorPingService}.
 *
 * <p><b>Shutdown:</b> {@code @PreDestroy} tenta encerrar o processo
 * graciosamente (SIGTERM) e, após {@link #SHUTDOWN_GRACE}, força o
 * encerramento (SIGKILL) se necessário. Isso evita deixar o Node.js
 * órfão quando a JVM é finalizada.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.tutor.freellmapi", name = "autostart", havingValue = "true")
class FreeLlmApiLauncher {

    private static final Logger log = LoggerFactory.getLogger(FreeLlmApiLauncher.class);
    private static final Duration MAX_STARTUP_WAIT = Duration.ofMinutes(3);
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(5);

    private final Path diretorio;
    private final int porta;
    private volatile Process processo;

    FreeLlmApiLauncher(TutorProperties props) {
        var cfg = props.freellmapi();
        this.diretorio = Path.of(cfg.dir()).toAbsolutePath().normalize();
        this.porta = cfg.porta();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        if (portaAberta()) {
            log.info("[FreeLLMAPI] Já está respondendo na porta {}; nada a iniciar.", porta);
            return;
        }
        if (!validarAmbienteNode()) {
            return;
        }

        // Não bloqueia a thread de inicialização: o proxy é opcional.
        Thread.ofVirtual().name("freellmapi-launcher").start(this::iniciarProxy);
    }

    private void iniciarProxy() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> comando = windows
                ? List.of("cmd", "/c", "npm", "run", "dev")
                : List.of("npm", "run", "dev");

        try {
            processo = new ProcessBuilder(comando)
                    .directory(diretorio.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(diretorio.resolve("proxy.log").toFile()))
                    .start();

            log.info("[FreeLLMAPI] Processo iniciado com PID {}", processo.pid());
            aguardarDisponibilidade();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[FreeLLMAPI] Interrompido enquanto aguardava o proxy iniciar.");
        } catch (IOException e) {
            log.warn("[FreeLLMAPI] Falha ao iniciar proxy ({}). A cascata segue sem ele.", e.getMessage());
        }
    }

    private boolean validarAmbienteNode() {
        var packageJson = diretorio.resolve("package.json");
        if (!Files.exists(packageJson) || !Files.isRegularFile(packageJson)) {
            log.warn("[FreeLLMAPI] package.json não encontrado em {}; proxy não iniciado.", diretorio);
            return false;
        }
        if (!Files.isDirectory(diretorio.resolve("node_modules"))) {
            log.warn("[FreeLLMAPI] node_modules ausente em {}; rode 'npm install'. Proxy não iniciado.", diretorio);
            return false;
        }
        return true;
    }

    private void aguardarDisponibilidade() throws InterruptedException {
        log.info("[FreeLLMAPI] Iniciando em {} (porta {}). Aguardando até {}s...",
                diretorio, porta, MAX_STARTUP_WAIT.toSeconds());

        for (int segundo = 1; segundo <= MAX_STARTUP_WAIT.toSeconds(); segundo++) {
            if (!processo.isAlive()) {
                log.error("[FreeLLMAPI] Processo Node.js encerrou prematuramente (exit code {}). Verifique proxy.log.",
                        processo.exitValue());
                return;
            }
            if (portaAberta()) {
                log.info("[FreeLLMAPI] Proxy pronto na porta {} ({}s).", porta, segundo);
                return;
            }
            TimeUnit.SECONDS.sleep(1);
        }
        log.warn("[FreeLLMAPI] Timeout após {}s. A cascata seguirá sem ele.", MAX_STARTUP_WAIT.toSeconds());
    }

    @PreDestroy
    public void encerrar() {
        var p = processo;
        if (p == null || !p.isAlive()) {
            return;
        }

        log.info("[FreeLLMAPI] Encerrando processo (PID {}) com graceful shutdown...", p.pid());
        p.descendants().forEach(ProcessHandle::destroy);
        p.destroy();

        try {
            if (!p.waitFor(SHUTDOWN_GRACE.toSeconds(), TimeUnit.SECONDS)) {
                log.warn("[FreeLLMAPI] Processo não encerrou em {}s; forçando kill.", SHUTDOWN_GRACE.toSeconds());
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
    }

    private boolean portaAberta() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", porta), 500);
            return true;
        } catch (IOException e) {
            log.debug("[FreeLLMAPI] Porta {} não respondeu: {}", porta, e.getMessage());
            return false;
        }
    }
}