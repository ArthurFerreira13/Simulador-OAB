package com.exame.ordem.legislacao.internal;

import com.exame.ordem.legislacao.service.FonteLegislativa;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
class FonteLegislativaClient implements FonteLegislativa {

    private static final Logger log = LoggerFactory.getLogger(FonteLegislativaClient.class);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final Path cacheDir;
    private final OkHttpClient okHttpClient;
    private final Map<String, List<String>> urlsAlternativasPorChave = new ConcurrentHashMap<>();

    FonteLegislativaClient(
            @Value("${app.legislacao.cache-dir:data/cache-legislacao}") String cacheDirPath
    ) {
        this.cacheDir = Path.of(cacheDirPath);
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(15))
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build();
    }

    void registrarUrlsAlternativas(String chaveCache, List<String> urls) {
        if (chaveCache != null && !chaveCache.isBlank() && urls != null && !urls.isEmpty()) {
            urlsAlternativasPorChave.put(chaveCache, List.copyOf(urls));
        }
    }

    @Override
    public ResultadoColeta obterConteudo(String url, Path fallbackLocal, String chaveCache) {
        Path arquivoCache = resolverArquivoCache(chaveCache);
        Path arquivoMetaUrl = resolverArquivoMetaUrl(chaveCache);

        if (arquivoCache != null && Files.exists(arquivoCache)) {
            try {
                String emCache = Files.readString(arquivoCache, StandardCharsets.UTF_8);
                if (conteudoValido(emCache, chaveCache)) {
                    String urlCache = lerUrlCache(arquivoMetaUrl, url);
                    return new ResultadoColeta(emCache, urlCache);
                }
            } catch (IOException e) {
                log.warn("Falha ao ler cache {}: {}", arquivoCache, e.getMessage());
            }
        }

        Set<String> candidatas = new LinkedHashSet<>();
        if (url != null && !url.isBlank()) {
            candidatas.add(url.trim());
        }
        if (chaveCache != null) {
            List<String> extras = urlsAlternativasPorChave.get(chaveCache);
            if (extras != null) {
                candidatas.addAll(extras);
            }
        }

        for (String candidata : candidatas) {
            String remoto = baixarComRetries(candidata);
            if (conteudoValido(remoto, chaveCache)) {
                salvarCache(arquivoCache, arquivoMetaUrl, remoto, candidata);
                return new ResultadoColeta(remoto, candidata);
            }
            if (remoto != null && !remoto.isBlank()) {
                String trecho = remoto.replaceAll("\\s+", " ").trim();
                if (trecho.length() > 200) {
                    trecho = trecho.substring(0, 200) + "...";
                }
                log.warn("Conteúdo descartado de {} (chave={}): {}", candidata, chaveCache, trecho);
            }
        }

        if (fallbackLocal != null && Files.exists(fallbackLocal)) {
            try {
                String local = Files.readString(fallbackLocal, StandardCharsets.UTF_8);
                if (!local.isBlank()) {
                    String urlLocal = (url != null && !url.isBlank()) ? url.trim() : fallbackLocal.toUri().toString();
                    log.info("Usando fallback local para '{}': {}", chaveCache, fallbackLocal);
                    salvarCache(arquivoCache, arquivoMetaUrl, local, urlLocal);
                    return new ResultadoColeta(local, urlLocal);
                }
            } catch (IOException e) {
                log.warn("Falha ao ler fallback local {}: {}", fallbackLocal, e.getMessage());
            }
        }

        throw new IllegalStateException("Sem fonte disponível para '" + chaveCache + "'. URLs tentadas: " + new ArrayList<>(candidatas));
    }

    private boolean conteudoValido(String conteudo, String chaveCache) {
        if (conteudo == null || conteudo.isBlank()) {
            return false;
        }
        if (chaveCache != null && (chaveCache.startsWith("SENADO_LISTA_") || chaveCache.startsWith("SENADO_DETALHE_"))) {
            return true;
        }
        String lower = conteudo.toLowerCase(Locale.ROOT);
        if (lower.contains("<app-root") && !lower.contains("art.")) {
            return false;
        }
        return conteudo.length() >= 200 && (lower.contains("art.") || lower.contains("ementa") || lower.contains("<html") || lower.contains("<p"));
    }

    private String baixarComRetries(String url) {
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            try {
                return baixarViaOkHttp(url);
            } catch (Exception e) {
                log.warn("Falha HTTP em {} (tentativa {}/3): {}", url, tentativa, e.getMessage());
                if (tentativa < 3) {
                    try {
                        Thread.sleep(1000L * tentativa);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private String baixarViaOkHttp(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " (" + response.message() + ")");
            }
            ResponseBody body = response.body();
            if (body == null) {
                return "";
            }
            MediaType mediaType = body.contentType();
            Charset charset = mediaType != null ? mediaType.charset() : null;
            byte[] bytes = lerBytesResiliente(body.byteStream(), charset);
            if (bytes.length == 0) {
                throw new IOException("Corpo vazio (0 bytes)");
            }
            return decodificarBytes(bytes, charset);
        }
    }

    private byte[] lerBytesResiliente(InputStream in, Charset charset) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            int lidos;
            while ((lidos = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, lidos);
            }
            return buffer.toByteArray();
        } catch (EOFException | ProtocolException e) {
            byte[] parciais = buffer.toByteArray();
            String parcialTexto = decodificarBytes(parciais, charset).toLowerCase(Locale.ROOT);
            if (parcialTexto.contains("</html>") || parcialTexto.contains("</body>")) {
                log.warn("Conexão encerrada antes do terminador chunked, mas o HTML completo já foi recebido ({} bytes).", parciais.length);
                return parciais;
            }
            throw e;
        }
    }

    private String decodificarBytes(byte[] bytes, Charset declarado) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (declarado != null) {
            return new String(bytes, declarado);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    private Path resolverArquivoCache(String chaveCache) {
        if (chaveCache == null || chaveCache.isBlank()) {
            return null;
        }
        String sanitizado = chaveCache.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cacheDir.resolve(sanitizado + ".txt");
    }

    private Path resolverArquivoMetaUrl(String chaveCache) {
        if (chaveCache == null || chaveCache.isBlank()) {
            return null;
        }
        String sanitizado = chaveCache.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cacheDir.resolve(sanitizado + ".url");
    }

    private String lerUrlCache(Path arquivoMetaUrl, String fallbackUrl) {
        if (arquivoMetaUrl != null && Files.exists(arquivoMetaUrl)) {
            try {
                String salva = Files.readString(arquivoMetaUrl, StandardCharsets.UTF_8).trim();
                if (!salva.isBlank()) {
                    return salva;
                }
            } catch (IOException ignored) {
            }
        }
        return fallbackUrl;
    }

    private void salvarCache(Path arquivo, Path arquivoMetaUrl, String conteudo, String urlEfetiva) {
        if (arquivo == null || conteudo == null || conteudo.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
            if (arquivoMetaUrl != null && urlEfetiva != null && !urlEfetiva.isBlank()) {
                Files.writeString(arquivoMetaUrl, urlEfetiva.trim(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Não foi possível salvar cache em {}: {}", arquivo, e.getMessage());
        }
    }
}