package com.exame.ordem.legislacao.service;

import com.exame.ordem.legislacao.internal.Hashes;
import com.exame.ordem.legislacao.internal.LegislacaoRepository;
import com.exame.ordem.legislacao.internal.TextoLegalParser;
import com.exame.ordem.legislacao.internal.TextoNormalizador;
import com.exame.ordem.system.erro.excecoes.RegraDeNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Importação manual de normas (texto colado pelo administrador).
 *
 * <p>Idempotente: mesma norma + mesmo hash do texto normalizado não cria nova versão.
 * Só transações de banco; nenhuma chamada externa acontece aqui (a futura sincronização com
 * API externa deve buscar fora da transação e chamar este fluxo por norma).
 *
 * <p>Salvaguarda de publicação: uma versão com menos de 70% dos artigos da versão vigente não
 * é publicada sem confirmação explícita ({@code forcar}).
 */
@Service
public class ImportacaoLegislacaoService {

    private static final double LIMIAR_QUEDA_ARTIGOS = 0.70;

    public record ComandoImportacao(
            String identificadorExterno,
            String tipo,
            String numero,
            Integer ano,
            String nome,
            String ementa,
            Integer disciplinaId,
            String fonteUrl,
            String texto,
            boolean publicar
    ) {}

    public record ResultadoImportacao(
            UUID normaId,
            UUID versaoId,
            boolean novaVersao,
            String statusVersao,
            int artigos,
            boolean publicada,
            List<String> avisos
    ) {}

    public record ResultadoPublicacao(UUID versaoId, String statusVersao, int artigos) {}

    private final LegislacaoRepository repository;
    private final Clock clock;

    ImportacaoLegislacaoService(LegislacaoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ResultadoImportacao importar(ComandoImportacao c) {
        Instant agora = clock.instant();
        String texto = TextoNormalizador.normalizar(c.texto());
        var parse = TextoLegalParser.extrairArtigos(texto);
        String hash = Hashes.sha256(texto);

        UUID normaId = repository.upsertNorma(
                LegislacaoRepository.PROVEDOR_MANUAL, c.identificadorExterno(), c.tipo(), c.numero(),
                c.ano(), c.nome(), c.ementa(), c.disciplinaId(), c.fonteUrl(), agora);

        List<String> avisos = new ArrayList<>(parse.avisos());
        var existente = repository.versaoPorHash(normaId, hash);

        UUID versaoId;
        String status;
        boolean nova;
        if (existente.isPresent()) {
            versaoId = existente.get().id();
            status = existente.get().status();
            nova = false;
            if (!LegislacaoRepository.VALIDADA.equals(status) && !LegislacaoRepository.PUBLICADA.equals(status)) {
                avisos.add("Já existe uma versão idêntica com status " + status + ".");
            }
        } else {
            versaoId = repository.inserirVersao(normaId, hash, texto, agora);
            repository.inserirArtigos(versaoId, parse.artigos(), agora);
            status = LegislacaoRepository.VALIDADA;
            nova = true;
        }

        if (c.publicar() && LegislacaoRepository.VALIDADA.equals(status)) {
            Optional<String> bloqueio = motivoBloqueio(normaId, versaoId);
            if (bloqueio.isPresent()) {
                avisos.add("Publicação não realizada: " + bloqueio.get());
            } else {
                repository.publicarVersao(normaId, versaoId, LocalDate.now(clock));
                status = LegislacaoRepository.PUBLICADA;
            }
        }

        return new ResultadoImportacao(
                normaId, versaoId, nova, status, repository.contarArtigos(versaoId),
                LegislacaoRepository.PUBLICADA.equals(status), List.copyOf(avisos));
    }

    @Transactional
    public ResultadoPublicacao publicar(UUID versaoId, boolean forcar) {
        var versao = repository.versao(versaoId)
                .orElseThrow(() -> new RegraDeNegocioException("Versão não encontrada."));

        int artigos = repository.contarArtigos(versaoId);
        if (LegislacaoRepository.PUBLICADA.equals(versao.status())) {
            return new ResultadoPublicacao(versaoId, versao.status(), artigos);
        }
        if (!LegislacaoRepository.VALIDADA.equals(versao.status())) {
            throw new RegraDeNegocioException(
                    "Só versões VALIDADA podem ser publicadas (status atual: " + versao.status() + ").");
        }
        if (!forcar) {
            motivoBloqueio(versao.normaId(), versaoId).ifPresent(motivo -> {
                throw new RegraDeNegocioException("Publicação bloqueada: " + motivo
                        + " Use forcar=true após revisar o texto.");
            });
        }

        repository.publicarVersao(versao.normaId(), versaoId, LocalDate.now(clock));
        return new ResultadoPublicacao(versaoId, LegislacaoRepository.PUBLICADA, artigos);
    }

    private Optional<String> motivoBloqueio(UUID normaId, UUID versaoId) {
        int vigentes = repository.contarArtigosDaVersaoPublicada(normaId);
        int novos = repository.contarArtigos(versaoId);
        if (vigentes > 0 && novos < Math.ceil(vigentes * LIMIAR_QUEDA_ARTIGOS)) {
            return Optional.of("a nova versão tem " + novos + " artigos contra " + vigentes
                    + " da versão vigente (menos de 70%).");
        }
        return Optional.empty();
    }
}
