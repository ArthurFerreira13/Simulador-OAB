package com.exame.ordem.legislacao.service;

import com.exame.ordem.legislacao.LegislacaoException;
import com.exame.ordem.legislacao.dto.DadosNorma;
import com.exame.ordem.legislacao.internal.EstruturaLegalParser;
import com.exame.ordem.legislacao.internal.EstruturaLegalParser.Dispositivo;
import com.exame.ordem.legislacao.internal.Hashes;
import com.exame.ordem.legislacao.internal.LegislacaoRepository;
import com.exame.ordem.legislacao.internal.LegislacaoRepository.NovaVersao;
import com.exame.ordem.legislacao.internal.TextoBusca;
import com.exame.ordem.legislacao.internal.TextoNormalizador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class IngestaoLegislacaoService {

    private static final Logger log = LoggerFactory.getLogger(IngestaoLegislacaoService.class);

    private static final int TEXTO_MAXIMO = 8_000_000;
    private static final double LIMIAR_QUEDA_ARTIGOS = 0.70;
    private static final String PIPELINE = "ingestao-1/" + EstruturaLegalParser.VERSAO;

    public record ComandoIngestao(
            DadosNorma norma,
            Set<String> aliases,
            Set<Long> disciplinaIds,
            String fonte,
            String url,
            String formato,
            boolean consolidado,
            String texto,
            boolean forcarReprocessamento,
            boolean forcarPublicacao
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private DadosNorma norma;
            private Set<String> aliases = Set.of();
            private Set<Long> disciplinaIds = Set.of();
            private String fonte;
            private String url;
            private String formato;
            private boolean consolidado;
            private String texto;
            private boolean forcarReprocessamento;
            private boolean forcarPublicacao;

            public Builder norma(DadosNorma norma) { this.norma = norma; return this; }
            public Builder aliases(Set<String> aliases) { this.aliases = aliases; return this; }
            public Builder disciplinaIds(Set<Long> disciplinaIds) { this.disciplinaIds = disciplinaIds; return this; }
            public Builder fonte(String fonte) { this.fonte = fonte; return this; }
            public Builder url(String url) { this.url = url; return this; }
            public Builder formato(String formato) { this.formato = formato; return this; }
            public Builder consolidado(boolean consolidado) { this.consolidado = consolidado; return this; }
            public Builder texto(String texto) { this.texto = texto; return this; }
            public Builder forcarReprocessamento(boolean v) { this.forcarReprocessamento = v; return this; }
            public Builder forcarPublicacao(boolean v) { this.forcarPublicacao = v; return this; }

            public ComandoIngestao build() {
                return new ComandoIngestao(
                        norma, aliases, disciplinaIds, fonte, url, formato,
                        consolidado, texto, forcarReprocessamento, forcarPublicacao
                );
            }
        }
    }

    public enum Status { PUBLICADO, SEM_MUDANCA }

    public record ResultadoIngestao(
            UUID normaId, UUID jobId, UUID documentoId, Status status,
            int dispositivosNovos, int versoesNovas, int inalterados, int ausentesNoTexto,
            List<String> avisos) {}

    private final LegislacaoRepository repo;
    private final TransactionTemplate tx;
    private final Clock clock;

    IngestaoLegislacaoService(LegislacaoRepository repo, PlatformTransactionManager tm, Clock clock) {
        this.repo = repo;
        this.tx = new TransactionTemplate(tm);
        this.clock = clock;
    }

    public ResultadoIngestao ingerir(ComandoIngestao cmd) {
        validar(cmd);
        String normalizado = TextoNormalizador.normalizar(cmd.texto());
        String checksum = Hashes.sha256(normalizado);

        // 1) norma, documento e job (transação curta)
        Registro reg = tx.execute(status -> registrar(cmd, checksum));
        if (reg.semMudanca()) {
            log.info("Ingestão sem mudança (norma={}, documento={})", reg.normaId(), reg.documentoId());
            return new ResultadoIngestao(reg.normaId(), null, reg.documentoId(), Status.SEM_MUDANCA,
                    0, 0, 0, 0, List.of("Texto idêntico ao já publicado."));
        }

        // 2) interpretação e validação (sem transação)
        try {
            EstruturaLegalParser.Resultado parse = EstruturaLegalParser.analisar(normalizado);
            bloquearQuedaAnormal(reg.normaId(), parse, cmd.forcarPublicacao());

            // 3) escrita e publicação atômicas
            ResultadoIngestao r = tx.execute(status -> gravarEPublicar(reg, cmd, parse));
            log.info("Ingestão publicada (norma={}, job={}): {} novos, {} versões, {} inalterados",
                    reg.normaId(), reg.jobId(), r.dispositivosNovos(), r.versoesNovas(), r.inalterados());
            return r;
        } catch (RuntimeException e) {
            falharJob(reg.jobId(), e);
            if (e instanceof LegislacaoException le) throw le;
            throw new LegislacaoException("Falha na ingestão: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ etapa 1

    private record Registro(UUID normaId, UUID documentoId, UUID jobId, boolean semMudanca, boolean consolidado) {}

    private Registro registrar(ComandoIngestao cmd, String checksum) {
        UUID normaId = repo.upsertNorma(cmd.norma());

        // "Não informado" não é "apagar": só acrescenta, nunca remove aliases/disciplinas já curados.
        Set<String> aliases = aliasesNormalizados(cmd.aliases());
        if (!aliases.isEmpty()) {
            List<String> conflitos = repo.adicionarAliases(normaId, aliases);
            if (!conflitos.isEmpty()) {
                log.warn("Aliases que já pertencem a outra norma foram mantidos com o dono original "
                        + "(norma={}): {}", normaId, conflitos);
            }
        }
        if (cmd.disciplinaIds() != null && !cmd.disciplinaIds().isEmpty()) {
            repo.adicionarDisciplinas(normaId, cmd.disciplinaIds());
        }

        // Sem mudança só quando o texto é o do documento publicado AGORA. Um texto igual a uma versão
        // antiga já superada (A, depois B, depois A de novo) precisa ser reaplicado.
        var existente = repo.documentoPorChecksum(normaId, checksum);
        boolean jaEhOPublicado = existente.isPresent()
                && repo.documentoPublicadoAtual(normaId).filter(existente.get().id()::equals).isPresent();
        if (jaEhOPublicado && !cmd.forcarReprocessamento() && !cmd.forcarReprocessamento()) {
            return new Registro(normaId, existente.get().id(), null, true, cmd.consolidado());
        }
        UUID documentoId = existente.map(LegislacaoRepository.DocumentoExistente::id).orElseGet(() ->
                repo.inserirDocumento(normaId, cmd.fonte(), cmd.url(), cmd.formato(), cmd.consolidado(),
                        checksum, cmd.texto(), clock.instant(), EstruturaLegalParser.VERSAO));
        try {
            UUID jobId = repo.criarJob(normaId, documentoId, PIPELINE);
            return new Registro(normaId, documentoId, jobId, false, cmd.consolidado());
        } catch (DuplicateKeyException e) {
            throw new LegislacaoException("Já existe uma ingestão em andamento para esta norma.", e);
        }
    }

    // ------------------------------------------------------------------ etapa 2

    private void bloquearQuedaAnormal(UUID normaId, EstruturaLegalParser.Resultado parse, boolean forcar) {
        int anteriores = repo.contarArtigosAtuais(normaId);
        long novos = parse.quantidade(EstruturaLegalParser.Tipo.ARTIGO);
        if (!forcar && anteriores > 0 && novos < Math.ceil(anteriores * LIMIAR_QUEDA_ARTIGOS)) {
            throw new LegislacaoException("Publicação bloqueada: o texto tem " + novos + " artigos contra "
                    + anteriores + " publicados (menos de 70%). Revise o texto ou use forcarPublicacao.");
        }
    }

    // ------------------------------------------------------------------ etapa 3

    private ResultadoIngestao gravarEPublicar(Registro reg, ComandoIngestao cmd, EstruturaLegalParser.Resultado parse) {
        Map<String, LegislacaoRepository.DispositivoExistente> existentes = repo.dispositivosDaNorma(reg.normaId());
        Map<String, UUID> idPorChave = new HashMap<>();
        List<NovaVersao> versoes = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        int novos = 0, inalterados = 0;

        for (Dispositivo d : parse.dispositivos()) {
            vistas.add(d.chaveLogica());
            UUID parentId = d.chaveLogicaPai() == null ? null : idPorChave.get(d.chaveLogicaPai());
            var atual = existentes.get(d.chaveLogica());

            String situacao = situacaoParaGravar(d, reg.consolidado());
            String textoNorm = d.texto().replaceAll("\\s+", " ").strip();
            String hashNorm = Hashes.sha256(textoNorm);

            UUID dispositivoId;
            int numeroVersao;
            UUID anterior = null;
            if (atual == null) {
                dispositivoId = repo.inserirDispositivo(reg.normaId(), parentId, d.tipo().name(),
                        d.identificador(), d.chaveLogica(), d.caminho(), d.ordem());
                numeroVersao = 1;
                novos++;
            } else {
                dispositivoId = atual.id();
                if (atual.ordem() != d.ordem() || !d.caminho().equals(atual.caminho())
                        || !java.util.Objects.equals(parentId, atual.parentId())) {
                    repo.atualizarPosicao(dispositivoId, parentId, d.caminho(), d.ordem());
                }
                if (hashNorm.equals(atual.hashAtual()) && situacao.equals(atual.situacaoAtual())) {
                    idPorChave.put(d.chaveLogica(), dispositivoId);
                    inalterados++;
                    continue;
                }
                numeroVersao = atual.ultimaVersao() + 1;
                anterior = atual.versaoAtualId();
            }
            idPorChave.put(d.chaveLogica(), dispositivoId);
            versoes.add(new NovaVersao(dispositivoId, numeroVersao, d.texto(), textoNorm,
                    Hashes.sha256(d.texto()), hashNorm, situacao, d.tipoAlteracao().name(),
                    d.normaAlteradora(), anterior, reg.documentoId(), reg.jobId()));
        }

        repo.inserirVersoes(versoes);
        int promovidas = repo.publicarJob(reg.normaId(), reg.jobId());
        repo.atualizarJob(reg.jobId(), "PUBLISHED", "PUBLISHED", null);

        int ausentes = (int) existentes.keySet().stream().filter(k -> !vistas.contains(k)).count();
        List<String> avisos = new ArrayList<>(parse.avisos());
        if (ausentes > 0) {
            avisos.add(ausentes + " dispositivo(s) já publicado(s) não aparecem neste texto; foram mantidos "
                    + "sem alteração (ausência no texto não é revogação).");
        }
        return new ResultadoIngestao(reg.normaId(), reg.jobId(), reg.documentoId(),
                promovidas > 0 ? Status.PUBLICADO : Status.SEM_MUDANCA,
                novos, versoes.size(), inalterados, ausentes, List.copyOf(avisos));
    }

    static String situacaoParaGravar(Dispositivo d, boolean consolidado) {
        return switch (d.situacao()) {
            case REVOGADO -> "REVOGADO";
            case VETADO -> "VETADO";
            case UNKNOWN -> consolidado ? "VIGENTE" : "UNKNOWN";
        };
    }

    private void falharJob(UUID jobId, RuntimeException e) {
        try {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            tx.executeWithoutResult(s -> repo.atualizarJob(jobId, "FAILED", "FAILED",
                    msg.length() > 2000 ? msg.substring(0, 2000) : msg));
        } catch (RuntimeException ex) {
            log.error("Não foi possível marcar o job {} como FAILED", jobId, ex);
        }
    }

    // ------------------------------------------------------------------ util

    private static Set<String> aliasesNormalizados(Set<String> aliases) {
        Set<String> saida = new LinkedHashSet<>();
        if (aliases != null) {
            for (String a : aliases) {
                String n = TextoBusca.normalizar(a);
                if (!n.isBlank() && n.length() <= 120) saida.add(n);
            }
        }
        return saida;
    }

    private static void validar(ComandoIngestao c) {
        if (c == null || c.norma() == null) throw new LegislacaoException("Dados da norma são obrigatórios.");
        if (c.texto() == null || c.texto().isBlank()) throw new LegislacaoException("Texto da norma é obrigatório.");
        if (c.texto().length() > TEXTO_MAXIMO) throw new LegislacaoException("Texto excede o tamanho máximo.");
        if (c.fonte() == null || c.fonte().isBlank()) throw new LegislacaoException("Fonte é obrigatória.");
        if (c.formato() == null || c.formato().isBlank()) throw new LegislacaoException("Formato é obrigatório.");
        if (c.norma().epigrafe() == null || c.norma().epigrafe().isBlank()) {
            throw new LegislacaoException("Epígrafe da norma é obrigatória.");
        }
    }
}