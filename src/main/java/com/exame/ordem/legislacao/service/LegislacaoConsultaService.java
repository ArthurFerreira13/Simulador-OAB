package com.exame.ordem.legislacao.service;

import com.exame.ordem.legislacao.LegislacaoConsulta;
import com.exame.ordem.legislacao.dto.ContextoConsultaLegislacao;
import com.exame.ordem.legislacao.dto.ResultadoLegislacao;
import com.exame.ordem.legislacao.dto.TrechoNormativo;
import com.exame.ordem.legislacao.internal.LegislacaoRepository;
import com.exame.ordem.legislacao.internal.TextoBusca;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consulta somente local sobre o que foi PUBLICADO: resolução exata de referência
 * ("art. 5º da CF") com expansão pai-filho, e busca textual como complemento. A Fase B acrescenta
 * a busca vetorial e a fusão por RRF, sem mudar este contrato.
 */
@Service
class LegislacaoConsultaService implements LegislacaoConsulta {

    private static final int MAX_ARTIGOS_POR_PERGUNTA = 3;
    private static final int MAX_TRECHOS_POR_ARTIGO = 40;

    /** Lê o texto sem acento e com pontuação: "art. 121-A", "artigo 5". Sufixo só com hífen. */
    private static final Pattern ARTIGO_REF = Pattern.compile(
            "\\bart(?:igos?)?\\.?\\s*(\\d+(?:\\.\\d{3})*)(?:\\s*-\\s*([a-z])\\b)?");

    private final LegislacaoRepository repo;
    private final Clock clock;
    private final double minimoRank;
    private final LoadingCache<String, List<String>> aliases;

    LegislacaoConsultaService(LegislacaoRepository repo, Clock clock,
                              @Value("${app.legislacao.consulta.min-rank:0.15}") double minimoRank) {
        this.repo = repo;
        this.clock = clock;
        this.minimoRank = minimoRank;
        // Poucas dezenas de apelidos: recarregados a cada 5 min (uma ingestão nova vale em até 5 min).
        this.aliases = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build(k -> repo.todosAliases().stream()
                        .sorted(Comparator.comparingInt(String::length).reversed())
                        .toList());
    }

    @Override
    public ResultadoLegislacao buscar(ContextoConsultaLegislacao c) {
        if (c.dataReferencia() != null && c.dataReferencia().isBefore(LocalDate.now(clock))) {
            return ResultadoLegislacao.semBase("Consulta por data passada ainda não é suportada: as versões "
                    + "históricas não têm vigência verificada.");
        }

        Optional<UUID> norma = acharNorma(TextoBusca.normalizar(c.pergunta()));
        Map<UUID, TrechoNormativo> escolhidos = new LinkedHashMap<>();
        boolean truncou = false;

        if (norma.isPresent()) {
            for (String chave : artigosCitados(c.pergunta())) {
                for (TrechoNormativo t : repo.artigoComFilhos(norma.get(), chave, MAX_TRECHOS_POR_ARTIGO)) {
                    if (escolhidos.size() >= c.limite()) {
                        truncou = true;
                        break;
                    }
                    escolhidos.putIfAbsent(t.versaoDispositivoId(), t);
                }
            }
        }
        boolean temExato = !escolhidos.isEmpty();

        int restantes = c.limite() - escolhidos.size();
        if (restantes > 0) {
            for (TrechoNormativo t : repo.buscaTextual(c.pergunta(), c.disciplinaId(), norma.orElse(null),
                    restantes, minimoRank)) {
                escolhidos.putIfAbsent(t.versaoDispositivoId(), t);
            }
        }

        List<TrechoNormativo> trechos = new ArrayList<>(escolhidos.values());
        if (trechos.isEmpty()) {
            return ResultadoLegislacao.semBase("Nenhum trecho publicado com evidência suficiente para esta pergunta.");
        }
        return new ResultadoLegislacao(trechos, true, aviso(trechos, temExato, truncou));
    }

    @Override
    public Optional<TrechoNormativo> trecho(UUID versaoDispositivoId) {
        return repo.trechoPorVersao(versaoDispositivoId);
    }

    // ------------------------------------------------------------------ interno

    private Optional<UUID> acharNorma(String perguntaNormalizada) {
        String corpo = " " + perguntaNormalizada + " ";
        for (String alias : aliases.get("todos")) {
            if (corpo.contains(" " + alias + " ")) {
                return repo.normaPorAlias(alias);
            }
        }
        return Optional.empty();
    }

    /** Chaves lógicas dos artigos citados ("art-5", "art-121-A"), sem repetição, até o máximo. */
    static Set<String> artigosCitados(String pergunta) {
        Set<String> chaves = new LinkedHashSet<>();
        Matcher m = ARTIGO_REF.matcher(TextoBusca.minusculoSemAcento(pergunta));
        while (m.find() && chaves.size() < MAX_ARTIGOS_POR_PERGUNTA) {
            String letra = m.group(2) == null ? "" : "-" + m.group(2).toUpperCase();
            chaves.add("art-" + m.group(1).replace(".", "") + letra);
        }
        return chaves;
    }

    private static String aviso(List<TrechoNormativo> trechos, boolean temExato, boolean truncou) {
        List<String> avisos = new ArrayList<>();
        if (trechos.stream().anyMatch(t -> !t.situacao().verificada())) {
            avisos.add("Há trechos com vigência não verificada pela fonte: não apresente como vigentes.");
        }
        if (!temExato) {
            avisos.add("Trechos recuperados por busca textual; confirme se respondem à pergunta antes de citar.");
        }
        if (truncou) {
            avisos.add("Resultado limitado ao máximo de trechos pedido.");
        }
        return avisos.isEmpty() ? null : String.join(" ", avisos);
    }
}
