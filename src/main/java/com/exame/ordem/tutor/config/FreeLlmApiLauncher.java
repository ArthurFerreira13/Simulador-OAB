package com.exame.ordem.tutor.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Sobe o proxy FreeLLMAPI (processo Node separado) junto com a aplicação, só em desenvolvimento.
 *
 * <p>Existe apenas no profile {@code dev} e com {@code app.tutor.freellmapi.autostart=true}.
 * Se a porta já estiver respondendo, assume que o proxy foi iniciado manualmente e não faz nada
 * (nem o encerra no shutdown).
 *
 * <p>Escuta {@link ApplicationStartedEvent}, que dispara antes dos {@code CommandLineRunner}
 * (indexadores): o proxy não fica esperando um runner longo terminar.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.tutor.freellmapi", name = "autostart", havingValue = "true")
public class FreeLlmApiLauncher {

    private static final Logger log = LoggerFactory.getLogger(FreeLlmApiLauncher.class);

    private final Path diretorio;
    private final int porta;
    private Process processo;

    public FreeLlmApiLauncher(
            @Value("${app.tutor.freellmapi.dir:infra/freellmapi-main}") String diretorio,
            @Value("${app.tutor.freellmapi.porta:3001}") int porta) {
        this.diretorio = Path.of(diretorio).toAbsolutePath().normalize();
        this.porta = porta;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void iniciar() {
        if (portaAberta()) {
            log.info("[FreeLLMAPI] já está respondendo na porta {}; nada a iniciar.", porta);
            return;
        }
        if (!Files.exists(diretorio.resolve("package.json"))) {
            log.warn("[FreeLLMAPI] package.json não encontrado em {}; proxy não iniciado.", diretorio);
            return;
        }
        if (!Files.isDirectory(diretorio.resolve("node_modules"))) {
            log.warn("[FreeLLMAPI] node_modules ausente em {}; rode 'npm install' nessa pasta. Proxy não iniciado.",
                    diretorio);
            return;
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> comando = windows
                ? List.of("cmd", "/c", "npm", "run", "dev")
                : List.of("npm", "run", "dev");

        try {
            File logProxy = diretorio.resolve("proxy.log").toFile();
            processo = new ProcessBuilder(comando)
                    .directory(diretorio.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logProxy))
                    .start();
            log.info("[FreeLLMAPI] iniciando em {} (porta {}, log em {}). Leva alguns segundos para ficar pronto.",
                    diretorio, porta, logProxy);
        } catch (IOException e) {
            log.warn("[FreeLLMAPI] não foi possível iniciar o proxy ({}). A cascata segue sem ele.", e.getMessage());
        }
    }

    @PreDestroy
    public void encerrar() {
        if (processo == null || !processo.isAlive()) {
            return;
        }
        log.info("[FreeLLMAPI] encerrando o proxy...");
        // No Windows o npm cria uma árvore (cmd > npm > node): encerra os filhos primeiro.
        List<ProcessHandle> filhos = processo.descendants().toList();
        filhos.forEach(ProcessHandle::destroyForcibly);
        processo.destroyForcibly();
    }

    private boolean portaAberta() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", porta), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}