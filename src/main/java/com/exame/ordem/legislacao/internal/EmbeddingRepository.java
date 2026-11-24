package com.exame.ordem.legislacao.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Repositório de embeddings da legislação. Não realiza chamadas a serviços externos. */
@Repository
public class EmbeddingRepository {

    public static final UUID INICIO = new UUID(0L, 0L);

    private static final Pattern IDENTIFICADOR_SQL_SEGURO = Pattern.compile("^[a-z_][a-z0-9_]*$");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate somenteLeitura;

    EmbeddingRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc não pode ser nulo");
        this.somenteLeitura = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager não pode ser nulo")
        );
        this.somenteLeitura.setReadOnly(true);
    }

    /**
     * Busca versões atuais ainda sem embedding para o modelo e pipeline informados.
     * O cursor por ID permite avançar mesmo quando algum item falha na indexação.
     */
    public List<DispositivoPendenteEmbedding> pendentes(
            PerfilEmbedding perfil,
            String modelo,
            String versaoPipeline,
            UUID aposVersaoId,
            int limite
    ) {
        String tabela = exigirTabelaValida(perfil);
        Objects.requireNonNull(modelo, "modelo não pode ser nulo");
        Objects.requireNonNull(versaoPipeline, "versaoPipeline não pode ser nulo");
        Objects.requireNonNull(aposVersaoId, "aposVersaoId não pode ser nulo");

        if (limite <= 0) {
            throw new IllegalArgumentException("limite deve ser maior que zero");
        }

        String sql = """
                SELECT v.id,
                       d.norma_id,
                       COALESCE(n.nome_curto, n.epigrafe) AS norma,
                       d.tipo,
                       d.caminho,
                       v.texto_original,
                       CASE WHEN d.tipo <> 'ARTIGO' THEN pv.texto_original END AS texto_pai
                  FROM legislacao_versao_dispositivo v
                  JOIN legislacao_dispositivo d ON d.id = v.dispositivo_id
                  JOIN legislacao_norma n ON n.id = d.norma_id
                  LEFT JOIN legislacao_versao_dispositivo pv
                         ON pv.dispositivo_id = d.parent_id AND pv.atual
                 WHERE v.atual
                   AND n.indexar_no_tutor
                   AND n.situacao <> 'REVOGADA'
                   AND d.tipo IN ('ARTIGO', 'PARAGRAFO', 'INCISO', 'ALINEA')
                   AND v.situacao NOT IN ('REVOGADO', 'VETADO')
                   AND v.id > ?
                   AND NOT EXISTS (
                       SELECT 1
                         FROM %s e
                        WHERE e.versao_dispositivo_id = v.id
                          AND e.modelo = ?
                          AND e.versao_pipeline = ?
                   )
                 ORDER BY v.id
                 LIMIT ?
                """.formatted(tabela);

        return jdbc.query(
                sql,
                (rs, rowNum) -> new DispositivoPendenteEmbedding(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7)
                ),
                aposVersaoId,
                modelo,
                versaoPipeline,
                limite
        );
    }

    /**
     * Insere os embeddings em lote via UNNEST e retorna a quantidade exata de linhas
     * efetivamente inseridas (ignorando duplicatas pela restrição de unicidade).
     */
    public int inserir(PerfilEmbedding perfil, List<NovoEmbedding> lote) {
        String tabela = exigirTabelaValida(perfil);
        Objects.requireNonNull(lote, "lote não pode ser nulo");

        if (lote.isEmpty()) {
            return 0;
        }

        validarLote(lote);

        int tamanho = lote.size();
        UUID[] versaoIds = new UUID[tamanho];
        UUID[] normaIds = new UUID[tamanho];
        String[] modelos = new String[tamanho];
        String[] versoesModelo = new String[tamanho];
        String[] versoesPipeline = new String[tamanho];
        String[] hashes = new String[tamanho];
        String[] textos = new String[tamanho];
        String[] vetores = new String[tamanho];

        for (int i = 0; i < tamanho; i++) {
            NovoEmbedding item = lote.get(i);
            versaoIds[i] = item.versaoId();
            normaIds[i] = item.normaId();
            modelos[i] = item.modelo();
            versoesModelo[i] = item.versaoModelo();
            versoesPipeline[i] = item.versaoPipeline();
            hashes[i] = item.hashContextualizado();
            textos[i] = item.textoEmbutido();
            vetores[i] = literal(item.vetor());
        }

        String sql = """
                WITH inseridos AS (
                    INSERT INTO %s (
                        versao_dispositivo_id,
                        norma_id,
                        provedor,
                        modelo,
                        versao_modelo,
                        dimensao,
                        versao_pipeline,
                        hash_contextualizado,
                        texto_embutido,
                        embedding
                    )
                    SELECT u.versao_dispositivo_id,
                           u.norma_id,
                           ?,
                           u.modelo,
                           u.versao_modelo,
                           ?,
                           u.versao_pipeline,
                           u.hash_contextualizado,
                           u.texto_embutido,
                           u.embedding::vector
                      FROM unnest(
                           ?::uuid[],
                           ?::uuid[],
                           ?::text[],
                           ?::text[],
                           ?::text[],
                           ?::text[],
                           ?::text[],
                           ?::text[]
                      ) AS u(
                           versao_dispositivo_id,
                           norma_id,
                           modelo,
                           versao_modelo,
                           versao_pipeline,
                           hash_contextualizado,
                           texto_embutido,
                           embedding
                      )
                    ON CONFLICT (versao_dispositivo_id, modelo, versao_pipeline) DO NOTHING
                    RETURNING 1
                )
                SELECT count(*) FROM inseridos
                """.formatted(tabela);

        Integer inseridos = jdbc.queryForObject(
                sql,
                Integer.class,
                perfil.provedor(),
                PerfilEmbedding.DIMENSAO,
                versaoIds,
                normaIds,
                modelos,
                versoesModelo,
                versoesPipeline,
                hashes,
                textos,
                vetores
        );

        return inseridos == null ? 0 : inseridos;
    }

    /** Desativa embeddings de versões que deixaram de ser a versão atual. */
    public int desativarObsoletos(PerfilEmbedding perfil) {
        String tabela = exigirTabelaValida(perfil);

        return jdbc.update("""
                UPDATE %s e
                   SET ativo = false
                  FROM legislacao_versao_dispositivo v
                 WHERE e.versao_dispositivo_id = v.id
                   AND e.ativo
                   AND NOT v.atual
                """.formatted(tabela));
    }

    public long contarAtivos(PerfilEmbedding perfil) {
        String tabela = exigirTabelaValida(perfil);

        Long quantidade = jdbc.queryForObject(
                "SELECT count(*) FROM %s WHERE ativo".formatted(tabela),
                Long.class
        );
        return quantidade == null ? 0L : quantidade;
    }

    /**
     * Busca vizinhos por distância de cosseno entre versões atuais publicadas.
     *
     * @param normaIds {@code null} busca em todas as normas elegíveis; lista vazia retorna vazio.
     */
    public List<CandidatoVetorial> buscaVetorial(
            PerfilEmbedding perfil,
            String modelo,
            String versaoPipeline,
            float[] consulta,
            List<UUID> normaIds,
            int limite,
            int efSearch,
            boolean iterativeScan
    ) {
        String tabela = exigirTabelaValida(perfil);
        Objects.requireNonNull(modelo, "modelo não pode ser nulo");
        Objects.requireNonNull(versaoPipeline, "versaoPipeline não pode ser nulo");
        validarVetorConsulta(consulta);

        if (limite <= 0) {
            throw new IllegalArgumentException("limite deve ser maior que zero");
        }
        if (efSearch <= 0) {
            throw new IllegalArgumentException("efSearch deve ser maior que zero");
        }
        if (normaIds != null && normaIds.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                WITH consulta AS (
                    SELECT ?::vector AS vetor
                )
                SELECT v.id,
                       1 - (e.embedding <=> consulta.vetor) AS relevancia
                  FROM %s e
                  CROSS JOIN consulta
                  JOIN legislacao_versao_dispositivo v
                    ON v.id = e.versao_dispositivo_id AND v.atual
                  JOIN legislacao_norma n
                    ON n.id = e.norma_id
                   AND n.indexar_no_tutor
                   AND n.situacao <> 'REVOGADA'
                 WHERE e.ativo
                   AND e.modelo = ?
                   AND e.versao_pipeline = ?
                """.formatted(tabela));

        List<Object> argumentos = new ArrayList<>();
        argumentos.add(literal(consulta));
        argumentos.add(modelo);
        argumentos.add(versaoPipeline);

        if (normaIds != null) {
            sql.append(" AND e.norma_id = ANY (?)\n");
            argumentos.add(normaIds.toArray(new UUID[0]));
        }

        sql.append(" ORDER BY e.embedding <=> consulta.vetor\n LIMIT ?");
        argumentos.add(limite);

        return somenteLeitura.execute(status -> {
            jdbc.queryForObject(
                    "SELECT set_config('hnsw.ef_search', ?, true)",
                    String.class,
                    Integer.toString(efSearch)
            );
            jdbc.queryForObject(
                    "SELECT set_config('hnsw.iterative_scan', ?, true)",
                    String.class,
                    iterativeScan ? "relaxed_order" : "off"
            );

            return jdbc.query(
                    sql.toString(),
                    (rs, rowNum) -> new CandidatoVetorial(
                            rs.getObject(1, UUID.class),
                            rs.getDouble(2)
                    ),
                    argumentos.toArray()
            );
        });
    }

    private static String exigirTabelaValida(PerfilEmbedding perfil) {
        Objects.requireNonNull(perfil, "perfil não pode ser nulo");
        String tabela = Objects.requireNonNull(perfil.tabela(), "tabela do perfil não pode ser nula");

        if (!IDENTIFICADOR_SQL_SEGURO.matcher(tabela).matches()) {
            throw new IllegalArgumentException("Nome de tabela inválido: " + tabela);
        }
        return tabela;
    }

    private static void validarLote(List<NovoEmbedding> lote) {
        for (int i = 0; i < lote.size(); i++) {
            NovoEmbedding embedding = Objects.requireNonNull(
                    lote.get(i), "embedding não pode ser nulo; índice: " + i
            );
            Objects.requireNonNull(embedding.versaoId(), "versaoId não pode ser nulo; índice: " + i);
            Objects.requireNonNull(embedding.normaId(), "normaId não pode ser nulo; índice: " + i);
            Objects.requireNonNull(embedding.modelo(), "modelo não pode ser nulo; índice: " + i);
            Objects.requireNonNull(embedding.versaoPipeline(), "versaoPipeline não pode ser nulo; índice: " + i);

            float[] vetor = Objects.requireNonNull(
                    embedding.vetor(), "vetor não pode ser nulo; índice: " + i
            );

            if (vetor.length != PerfilEmbedding.DIMENSAO) {
                throw new IllegalArgumentException(
                        "O vetor no índice " + i + " deve ter " + PerfilEmbedding.DIMENSAO
                                + " dimensões, mas tem " + vetor.length + "."
                );
            }
        }
    }

    private static void validarVetorConsulta(float[] vetor) {
        Objects.requireNonNull(vetor, "vetor de consulta não pode ser nulo");

        if (vetor.length != PerfilEmbedding.DIMENSAO) {
            throw new IllegalArgumentException(
                    "Vetor de consulta deve ter " + PerfilEmbedding.DIMENSAO + " dimensões."
            );
        }
    }

    static String literal(float[] vetor) {
        Objects.requireNonNull(vetor, "vetor não pode ser nulo");

        StringBuilder literal = new StringBuilder(vetor.length * 12).append('[');
        for (int i = 0; i < vetor.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(vetor[i]);
        }
        return literal.append(']').toString();
    }
}